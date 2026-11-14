package com.histmersion
package util.nonempty.derivation

import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.SchemaType

import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.nonempty.NonEmptyVector

/** A record holding a [[NonEmptyVector]], derived as every module derives one: the provider makes it a list. */
final case class Box(items: NonEmptyVector[Int], more: Option[NonEmptyVector[String]])
    derives JsonCodec,
      KindlingsSchema

object Box:
  given CanEqual[Box, Box] = CanEqual.derived

class DerivedCodecsSuite extends munit.FunSuite:

  test("a non-empty vector is written as the list of its elements and read back"):
    val box = Box(NonEmptyVector.of(1, 2, 3), Some(NonEmptyVector.of("a")))
    assertEquals(box.asJson.noSpaces, """{"items":[1,2,3],"more":["a"]}""")
    assertEquals(RawJson.decode[Box]("""{"items":[1,2,3],"more":["a"]}"""), Right(box))

  test("an empty list is refused, not read as a value"):
    assert(RawJson.decode[Box]("""{"items":[]}""").isLeft)
    assert(RawJson.decode[Box]("""{"items":[1],"more":[]}""").isLeft)

  test("a single value written bare is no list"):
    assert(RawJson.decode[Box]("""{"items":1}""").isLeft)

  test("the schema says an array of the elements"):
    val items = summon[KindlingsSchema[Box]].schema.schemaType match
      case SchemaType.SProduct(fields) => fields.find(_.name.name == "items").map(_.schema)
      case _                           => None
    assert(items.exists(_.schemaType.isInstanceOf[SchemaType.SArray[?, ?]]), s"$items")
