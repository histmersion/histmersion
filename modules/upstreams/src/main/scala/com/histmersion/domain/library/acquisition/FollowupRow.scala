package com.histmersion.domain.library.acquisition
final case class FollowupRow(
    title: Option[FollowupRow.Title] = None,
    year: Option[com.histmersion.plugin.Year] = None,
    doi: Option[com.histmersion.plugin.Doi] = None,
    arxiv: Option[FollowupRow.Arxiv] = None,
    citedBy: Option[FollowupRow.CitedBy] = None,
    isInfluential: FollowupRow.IsInfluential = _root_.com.histmersion.domain.library.acquisition.FollowupRow.IsInfluential(false),
    intents: List[FollowupRow.Intents] = Nil,
    contexts: List[FollowupRow.Contexts] = Nil
)

object FollowupRow:
  type Title = com.histmersion.plugin.WorkKey.Title
  val Title: _root_.com.histmersion.plugin.WorkKey.Title.type = _root_.com.histmersion.plugin.WorkKey.Title
  type Arxiv = com.histmersion.upstreams.ArxivId
  val Arxiv: _root_.com.histmersion.upstreams.ArxivId.type = _root_.com.histmersion.upstreams.ArxivId
  type CitedBy = CitedBy.T
  object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsInfluential = IsInfluential.T
  object IsInfluential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Intents = Intents.T
  object Intents extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contexts = Contexts.T
  object Contexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
