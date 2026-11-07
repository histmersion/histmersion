package com.histmersion.domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `facets` as an agent calls it: one field's values, optionally by prefix. */
final case class AgentFacetsInput(field: AgentFacetsInput.Field, prefix: Option[AgentFacetsInput.Prefix] = None) derives KindlingsCodecAsObject
object AgentFacetsInput:
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Prefix = com.histmersion.domain.library.search.FilterSql.FacetPrefix
  val Prefix: _root_.com.histmersion.domain.library.search.FilterSql.FacetPrefix.type = _root_.com.histmersion.domain.library.search.FilterSql.FacetPrefix

  given Schema[AgentFacetsInput] = KindlingsSchema
    .derived[AgentFacetsInput]
    .schema
    .modify(_.field)(_.description("tags|authors|places|topics|status"))
    .modify(_.prefix)(_.description("Only values starting with this"))
