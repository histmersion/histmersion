package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** `followups` as an agent may call it: no `apply` — a model never writes edges. */
final case class FollowupsInput(id: FollowupsInput.Id, since: Option[FollowupsInput.Since] = None, limit: Option[FollowupsInput.Limit] = None)
    derives KindlingsCodecAsObject
object FollowupsInput:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Since = Year
  val Since: Year.type = Year
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]

  given Schema[FollowupsInput] = KindlingsSchema
    .derived[FollowupsInput]
    .schema
    .modify(_.id)(_.description("A citekey, DOI or arxiv:<id>"))
    .modify(_.since)(_.description("Only papers published from this year on"))
    .modify(_.limit)(_.description("At most this many citing papers"))
