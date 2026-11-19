package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.search.FilterSql

/** `facets` as an agent calls it: one field's values, optionally by prefix. */
final case class AgentFacetsInput(field: AgentFacetsInput.Field, prefix: Option[AgentFacetsInput.Prefix] = None)
    derives JsonCodec
object AgentFacetsInput:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  export FilterSql.FacetPrefix as Prefix

  given Schema[AgentFacetsInput] = KindlingsSchema
    .derived[AgentFacetsInput]
    .schema
    .modify(_.field)(_.description("tags|authors|places|topics|status"))
    .modify(_.prefix)(_.description("Only values starting with this"))
