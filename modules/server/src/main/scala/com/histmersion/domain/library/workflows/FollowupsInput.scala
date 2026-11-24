package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** `followups` as an agent may call it: no `apply` — a model never writes edges. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class FollowupsInput(
    id: FollowupsInput.Id,
    since: Option[FollowupsInput.Since] = None,
    limit: Option[FollowupsInput.Limit] = None
) derives JsonCodec
object FollowupsInput:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Since = Year
  val Since: Year.type = Year
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[FollowupsInput] = KindlingsSchema
    .derived[FollowupsInput]
    .schema
    .modify(_.id)(_.description("A citekey, DOI or arxiv:<id>"))
    .modify(_.since)(_.description("Only papers published from this year on"))
    .modify(_.limit)(_.description("At most this many citing papers"))
