package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class FollowupRow(
    title: Option[Title] = None,
    year: Option[Year] = None,
    doi: Option[Doi] = None,
    arxiv: Option[FollowupRow.Arxiv] = None,
    citedBy: Option[FollowupRow.CitedBy] = None,
    isInfluential: FollowupRow.IsInfluential = FollowupRow.IsInfluential(false),
    intents: List[FollowupRow.Intents] = Nil,
    contexts: List[FollowupRow.Contexts] = Nil,
    proposedKind: FollowupRow.ProposedKind = FollowupRow.ProposedKind("cites"),
    citekey: Option[Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FollowupRow:
  type Arxiv = Arxiv.T
  object Arxiv extends RefinedType[String, Pure]
  export Acquire.{CitedByCount as CitedBy}
  type IsInfluential = IsInfluential.T
  object IsInfluential extends RefinedType[Boolean, Pure]
  type Intents = Intents.T
  object Intents extends RefinedType[String, Pure]
  type Contexts = Contexts.T
  object Contexts extends RefinedType[String, Pure]
  type ProposedKind = ProposedKind.T
  object ProposedKind extends RefinedType[String, Pure]
