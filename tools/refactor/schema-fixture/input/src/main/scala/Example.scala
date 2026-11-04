package schemafixture

import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

given hearth.kindlings.circederivation.Configuration = hearth.kindlings.circederivation.Configuration.default.withDefaults

final case class Plain(value: Int, note: Option[String] = None) derives KindlingsSchema
object Plain:
  given Schema[Plain] = summon[KindlingsSchema[Plain]].schema

object Nested:
  final case class Named(value: String) derives KindlingsSchema
  object Named:
    val retained = 42
    given custom: Schema[Named] = summon[KindlingsSchema[Named]].schema

final case class Customized(value: Int) derives KindlingsSchema
object Customized:
  given Schema[Customized] = summon[KindlingsSchema[Customized]].schema.description("keep me")

@main def verify(): Unit =
  import Nested.Named.{custom as namedSchema}
  assert(Plain.given_Schema_Plain.name == summon[Schema[Plain]].name)
  assert(Nested.Named.custom.name == namedSchema.name)
  assert(Nested.Named.retained == 42)
  assert(summon[Schema[Customized]].description.contains("keep me"))
  val actual = summon[Schema[Plain]].schemaType.asInstanceOf[sttp.tapir.SchemaType.SProduct[Plain]]
  assert(actual.fields.map(_.name.name) == List("value", "note"))
  assert(actual.fields.last.schema.isOptional)
