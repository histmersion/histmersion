package com.histmersion.domain.media.imports

/** A place as a collection match sees it: every name key it goes by (label and alternative names,
  * `histmersion_place_key`), its period — its own when bounded at both ends, else the span of the findings it holds —
  * and its Wikidata item and point (to tell equivalents apart).
  */
final case class PlaceFacts(
    id: PlaceFacts.Id,
    label: PlaceFacts.Label,
    keys: Set[PlaceFacts.Keys],
    period: Option[(PlaceFacts.PeriodPart1, PlaceFacts.PeriodPart2)],
    qid: Option[PlaceFacts.Qid],
    lat: Option[com.histmersion.Latitude],
    lon: Option[com.histmersion.Longitude]
)

object PlaceFacts:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart1 = com.histmersion.domain.DeepTime.SignedYear
  val PeriodPart1: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type PeriodPart2 = com.histmersion.domain.DeepTime.SignedYear
  val PeriodPart2: _root_.com.histmersion.domain.DeepTime.SignedYear.type = _root_.com.histmersion.domain.DeepTime.SignedYear
  type Qid = com.histmersion.domain.media.imports.Target.Qid
  val Qid: _root_.com.histmersion.domain.media.imports.Target.Qid.type = _root_.com.histmersion.domain.media.imports.Target.Qid
