package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class FollowupRow(
    title: Option[Title] = None,
    year: Option[Year] = None,
    doi: Option[Doi] = None,
    arxiv: Option[FollowupRow.Arxiv] = None,
    citedBy: Option[FollowupRow.CitedBy] = None,
    isInfluential: FollowupRow.IsInfluential = FollowupRow.IsInfluential.Ordinary,
    intents: List[FollowupRow.Intents] = Nil,
    contexts: List[FollowupRow.Contexts] = Nil,
    proposedKind: FollowupRow.ProposedKind = FollowupRow.ProposedKind("cites"),
    citekey: Option[Citekey] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FollowupRow:
  type Arxiv = Arxiv.T
  object Arxiv extends Newtype[String, Pure]
  export Acquire.CitedByCount as CitedBy
  enum IsInfluential derives CanEqual:
    case Influential, Ordinary
  object IsInfluential extends TwoStates[IsInfluential]:
    extension (isInfluential: IsInfluential) def isInfluential: Boolean = isInfluential == Influential
  type Intents = Intents.T
  object Intents extends Newtype[String, Pure]
  type Contexts = Contexts.T
  object Contexts extends Newtype[String, Pure]
  type ProposedKind = ProposedKind.T
  object ProposedKind extends Newtype[String, Pure]
