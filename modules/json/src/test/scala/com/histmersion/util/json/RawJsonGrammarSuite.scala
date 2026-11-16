package com.histmersion
package util.json

/** What raw JSON accepts and how it writes a number back, as Kindlings 0.3.4-67's `JsonNumber` decides: a number
  * outside the JSON grammar (`+1`, `01`, `1.`, `.5`) is refused, `-0` keeps its sign, and raw JSON is read by our own
  * reader, so Kindlings' depth limit of 128 (its `Json` codec's) does not apply to it. And how a derived codec reads an
  * enum case: as the object it writes (`{"Red":{}}`), not as a bare string, unless it is derived with words.
  */
class RawJsonGrammarSuite extends munit.FunSuite:
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

  test("a derived codec reads an enum case as the object it writes; a bare string only with words"):
    val codec = JsonCodec.derived[RawJsonGrammarSuite.Colour]
    val words = JsonCodec.derivedWords[RawJsonGrammarSuite.Colour]
    assertEquals(RawJson.decode("""{"Red":{}}""")(using codec), Right(RawJsonGrammarSuite.Colour.Red))
    assert(RawJson.decode("\"Red\"")(using codec).isLeft, "a bare string is no case of a circe-like codec")
    assertEquals(RawJson.decode("\"Red\"")(using words), Right(RawJsonGrammarSuite.Colour.Red))
    assertEquals(writeJson(RawJsonGrammarSuite.Colour.Red)(using codec), """{"Red":{}}""")

  private def writeJson[A](a: A)(using c: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[A]): String =
    com.github.plokhotnyuk.jsoniter_scala.core.writeToString(a)

object RawJsonGrammarSuite:
  enum Colour derives CanEqual:
    case Red, Blue
