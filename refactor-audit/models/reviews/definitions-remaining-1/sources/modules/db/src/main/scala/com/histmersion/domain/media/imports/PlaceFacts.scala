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
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart2 = PeriodPart2.T
  object PeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Qid = Qid.T
  object Qid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
