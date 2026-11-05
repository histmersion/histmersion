package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One import of an event catalogue, read as the server reads it: its parts, each part page by page.
  *
  * @param pages
  *   each page read (`part → events on it`, or why it could not be read)
  * @param accepted
  *   the events the server would store
  * @param refused
  *   the records it would refuse, with the reason (no id, a dating that runs backwards, …)
  * @param leftOut
  *   the values it would leave out of a record it keeps, with the reason (a position off the globe, a Wikidata id that
  *   is no item)
  */
final case class CatalogueReport(
    parts: List[CatalogueReport.Parts],
    pages: List[(CatalogueReport.PagesPart1, Either[CatalogueReport.PagesPart2Part1, CatalogueReport.PagesPart2Part2])],
    accepted: List[AcceptedEvent],
    refused: List[CatalogueReport.Refused],
    leftOut: List[CatalogueReport.LeftOut],
    failure: Option[CatalogueReport.Failure],
    log: List[LogLine],
    requests: List[CatalogueReport.Requests]
)

object CatalogueReport:
  type Parts = Parts.T
  object Parts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagesPart1 = PagesPart1.T
  object PagesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagesPart2Part1 = PagesPart2Part1.T
  object PagesPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagesPart2Part2 = PagesPart2Part2.T
  object PagesPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refused = Refused.T
  object Refused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeftOut = LeftOut.T
  object LeftOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Failure = Failure.T
  object Failure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Requests = Requests.T
  object Requests extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
