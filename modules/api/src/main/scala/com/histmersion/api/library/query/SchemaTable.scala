package com.histmersion
package api.library.query

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SchemaTable(name: SchemaTable.Name, kind: SchemaTable.Kind, columns: List[SchemaColumn])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SchemaTable:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
