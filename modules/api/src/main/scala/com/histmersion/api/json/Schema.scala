package com.histmersion
package api.json

import scala.language.adhocExtensions
final class Schema[A] private (schema: sttp.tapir.Schema[A]) extends sttp.tapir.Schema[A](
  schema.schemaType, schema.name, schema.isOptional, schema.description, schema.default,
  schema.format, schema.encodedExample, schema.deprecated, schema.hidden, schema.validator, schema.attributes
)
object Schema:
  def derived[A](using instance: hearth.kindlings.tapirschemaderivation.KindlingsSchema[A]): Schema[A] =
    new Schema(instance.schema)
