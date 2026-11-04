package schemafixture

import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

given hearth.kindlings.circederivation.Configuration = hearth.kindlings.circederivation.Configuration.default.withDefaults

final case class Plain(value: Int, note: Option[String] = None) derives KindlingsSchema, schemafixture.adapter.Schema


object Nested:
  final case class Named(value: String) derives KindlingsSchema, schemafixture.adapter.Schema
  object Named:
    val retained = 42


final case class Customized(value: Int) derives KindlingsSchema
object Customized:
  given Schema[Customized] = summon[KindlingsSchema[Customized]].schema.description("keep me")

@main def verify(): Unit =
  import Nested.Named.{derived$Schema as namedSchema}
  assert(Plain.derived$Schema.name == summon[Schema[Plain]].name)
  assert(Nested.Named.derived$Schema.name == namedSchema.name)
  assert(Nested.Named.retained == 42)
  assert(summon[Schema[Customized]].description.contains("keep me"))
  val actual = summon[Schema[Plain]].schemaType.asInstanceOf[sttp.tapir.SchemaType.SProduct[Plain]]
  assert(actual.fields.map(_.name.name) == List("value", "note"))
  assert(actual.fields.last.schema.isOptional)
