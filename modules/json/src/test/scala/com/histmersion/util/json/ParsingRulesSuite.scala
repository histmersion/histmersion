package com.histmersion
package util.json

/** Code words, as the last test reads them: no codec of their own. */
enum Colour derives CanEqual:
  case Red, Blue

/** What raw JSON accepts and how it writes a number back, as Kindlings 0.4.0's `JsonNumber` decides: a number outside
  * the JSON grammar (`+1`, `01`, `1.`, `.5`) is refused, `-0` keeps its sign, and raw JSON is read by our own reader,
  * so Kindlings' depth limit of 128 (its `Json` codec's) does not apply to it. And how a derived codec reads an enum
  * case: as the word it writes (`"Red"`), and as the object a former version wrote (`{"Red":{}}`).
  */
class ParsingRulesSuite extends munit.FunSuite:
  import RawJson.noSpaces

  test("a number outside the JSON grammar is refused"):
    List("+1", "01", "1.", ".5", "-01").foreach { text =>
      assert(RawJson.parse(text).isLeft, s"$text read as ${RawJson.parse(text)}")
      assert(RawJson.parse(s"""{"n":$text}""").isLeft, s"$text inside an object")
    }
    List("0", "-0", "1", "-1.5", "1e3", "1E+3", "0.5", "-0.0").foreach { text =>
      assert(RawJson.parse(text).isRight, s"$text refused: ${RawJson.parse(text)}")
    }

  test("negative zero keeps its sign when raw JSON is written back"):
    assertEquals(RawJson.parse("""{"b":-0,"c":-0.0,"d":0}""").map(_.noSpaces), Right("""{"b":-0,"c":-0.0,"d":0}"""))

  test("raw JSON nested deeper than 128 levels still reads: no depth limit on our reader"):
    val deep = "[" * 200 + "]" * 200
    assertEquals(RawJson.parse(deep).map(_.noSpaces), Right(deep))

  test("a derived codec writes an enum of words as the word, and reads the word and the object a former version wrote"):
    val codec = JsonCodec.derived[Colour]
    val words = JsonCodec.derivedWords[Colour]
    assertEquals(writeJson(Colour.Red)(using codec), "\"Red\"")
    assertEquals(writeJson(Colour.Red)(using words), "\"Red\"")
    assertEquals(RawJson.decode("\"Red\"")(using codec), Right(Colour.Red))
    assertEquals(RawJson.decode("""{"Red":{}}""")(using codec), Right(Colour.Red))
    assertEquals(RawJson.decode("\"Red\"")(using words), Right(Colour.Red))
    assert(RawJson.decode("\"Green\"")(using codec).isLeft, "a word that is no case")

  private def writeJson[A](a: A)(using c: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[A]): String =
    com.github.plokhotnyuk.jsoniter_scala.core.writeToString(a)
