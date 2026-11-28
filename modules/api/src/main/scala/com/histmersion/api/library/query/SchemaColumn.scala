package com.histmersion
package api.library.query

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SchemaColumn(
    name: SchemaColumn.Name,
    dataType: SchemaColumn.DataType,
    nullable: SchemaColumn.Nullable
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SchemaColumn:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type DataType = DataType.T
  object DataType extends Newtype[String, Pure]
  enum Nullable derives CanEqual:
    case Nullable, Required
  object Nullable extends TwoStates[Nullable]:
    extension (nullable: Nullable) def nullable: Boolean = nullable == Nullable
