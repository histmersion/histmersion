package com.histmersion
package util.json

import RawJson.*

final case class Inner(word: String) derives JsonCodec

/** A record with each kind of field a derived codec reads differently when it is absent. */
final case class Shape(
    name: String,
    note: Option[String],
    inner: Inner,
    tags: List[String],
    count: Int = 7,
    more: List[String] = List("m")
) derives JsonCodec

object Shape:
  given CanEqual[Shape, Shape] = CanEqual.derived

  /** The same record read as an MCP tool's arguments, where an unknown key is refused. */
  val strict: JsonCodec[Shape] = JsonCodec.derived[Shape](using JsonCodec.Unknown.Refused)

enum Kind derives JsonCodec, CanEqual:
  case Plain
  case Named(name: String)

/** Code words, as the problems' cases have them: no codec of their own, written by the record's. */
enum Code derives CanEqual:
  case Read, Write

final case class Coded(code: Code)

object Coded:
  val words: JsonCodec[Coded] = JsonCodec.derivedWords[Coded]

/** How a derived codec reads an absent field: an `Option` is `None`, a default is taken, anything else is missing; and
  * what it writes: every field, `None` as `null`.
  */
class AbsentFieldsSuite extends munit.FunSuite:

  private val full = """{"name":"a","note":"n","inner":{"word":"w"},"tags":["t"],"count":1,"more":["x"]}"""

  test("every field is written, None as null and a default too"):
    assertEquals(
      Shape("a", None, Inner("w"), Nil).asJson.noSpaces,
      """{"name":"a","note":null,"inner":{"word":"w"},"tags":[],"count":7,"more":["m"]}"""
    )

  test("a record read with every field is the record written"):
    assertEquals(
      RawJson.decode[Shape](full),
      Right(Shape("a", Some("n"), Inner("w"), List("t"), 1, List("x")))
    )

  test("an empty list where the default is not empty reads as the default, so a record's collection defaults are empty"):
    // jsoniter-scala's rule, which Kindlings follows: what is written as [] does not read back as empty
    assertEquals(
      RawJson.decode[Shape]("""{"name":"a","inner":{"word":"w"},"tags":[],"more":[]}""").map(_.more),
      Right(List("m"))
    )

  test("an absent Option is None, an absent field with a default takes it"):
    assertEquals(
      RawJson.decode[Shape]("""{"name":"a","inner":{"word":"w"},"tags":[]}"""),
      Right(Shape("a", None, Inner("w"), Nil, 7, List("m")))
    )

  test("an absent required field is missing, named in the message"):
    assertEquals(
      RawJson.decode[Shape]("""{"inner":{"word":"w"},"tags":[]}""").left.map(_.contains("missing required field \"name\"")),
      Left(true)
    )

  test("an absent collection without a default is missing, as any other required field"):
    assertEquals(
      RawJson.decode[Shape]("""{"name":"a","inner":{"word":"w"}}""").left.map(_.contains("missing required field \"tags\"")),
      Left(true)
    )

  test("a nested record's missing field is located inside it"):
    val why = RawJson.decode[Shape]("""{"name":"a","inner":{},"tags":[]}""")
    assert(why.left.exists(w => w.startsWith("inner") && w.contains("missing required field \"word\"")), why)

  test("null is no value of a required field"):
    assert(RawJson.decode[Shape]("""{"name":null,"inner":{"word":"w"},"tags":[]}""").isLeft)
    assert(RawJson.decode[Shape]("""{"name":"a","inner":null,"tags":[]}""").isLeft)

  test("an unknown key is skipped, unless the codec refuses it"):
    val extra = """{"name":"a","inner":{"word":"w"},"tags":[],"zz":1}"""
    assert(RawJson.decode[Shape](extra).isRight)
    assert(RawJson.decode[Shape](extra)(using Shape.strict).isLeft)

  test("an enum case without fields is an empty object, or a word where the codec writes words"):
    assertEquals(com.github.plokhotnyuk.jsoniter_scala.core.writeToString(Coded(Code.Read))(using Coded.words), """{"code":"Read"}""")
    assertEquals(Kind.Plain.asJson.noSpaces, """{"Plain":{}}""")
    assertEquals(RawJson.decode[Kind]("""{"Named":{"name":"x"}}"""), Right(Kind.Named("x")))
