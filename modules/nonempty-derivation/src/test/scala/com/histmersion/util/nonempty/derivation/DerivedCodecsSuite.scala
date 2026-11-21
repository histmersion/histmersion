package com.histmersion
package util.nonempty.derivation

import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.{Schema, SchemaType, Validator}

import com.histmersion.util.json.{JsonCodec, RawJson}
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.nonempty.NonEmptyVector

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

  private def minSizes(v: Validator[?]): List[Int] = v match
    case Validator.MinSize(n)   => List(n)
    case Validator.All(vs)      => vs.toList.flatMap(minSizes)
    case Validator.Mapped(w, _) => minSizes(w)
    case _                      => Nil

  private def field(name: String): Option[Schema[?]] = summon[KindlingsSchema[Box]].schema.schemaType match
    case SchemaType.SProduct(fields) => fields.find(_.name.name == name).map(_.schema)
    case _                           => None

  test("the schema says the list is required and has at least one element (minItems 1), as the reader does"):
    val items = field("items")
    assert(items.exists(!_.isOptional), s"$items")
    assertEquals(items.map(s => minSizes(s.validator)), Some(List(1)))

  test("an optional non-empty vector is optional, and still has at least one element when present"):
    val more = field("more")
    assert(more.exists(_.isOptional), s"$more")
    val present = more.map(_.schemaType).collect { case SchemaType.SOption(element) => element }
    assertEquals(present.map(s => minSizes(s.validator)), Some(List(1)))
