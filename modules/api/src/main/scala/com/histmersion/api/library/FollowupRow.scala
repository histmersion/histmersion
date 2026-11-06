package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FollowupRow(
    title: Option[com.histmersion.api.library.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    doi: Option[com.histmersion.api.library.Doi] = None,
    arxiv: Option[FollowupRow.Arxiv] = None,
    citedBy: Option[FollowupRow.CitedBy] = None,
    isInfluential: FollowupRow.IsInfluential = _root_.com.histmersion.api.library.FollowupRow.IsInfluential(false),
    intents: List[FollowupRow.Intents] = Nil,
    contexts: List[FollowupRow.Contexts] = Nil,
    proposedKind: FollowupRow.ProposedKind = _root_.com.histmersion.api.library.FollowupRow.ProposedKind("cites"),
    citekey: Option[com.histmersion.api.library.Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FollowupRow:
  type Arxiv = Arxiv.T
  object Arxiv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsInfluential = IsInfluential.T
  object IsInfluential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Intents = Intents.T
  object Intents extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contexts = Contexts.T
  object Contexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProposedKind = ProposedKind.T
  object ProposedKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
