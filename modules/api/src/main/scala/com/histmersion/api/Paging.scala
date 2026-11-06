package com.histmersion.api

import sttp.tapir.*
import com.histmersion.api.automation.ListFilters

/** Paging travels in response headers so the JSON body stays the plain array older CLIs and MCP clients read. */
object Paging:

  type EncodeCountsResult = EncodeCountsResult.T
  object EncodeCountsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeCountsCountsPart1 = _root_.com.histmersion.api.Paging.Page.CountsPart1
  val EncodeCountsCountsPart1: _root_.com.histmersion.api.Paging.Page.CountsPart1.type = _root_.com.histmersion.api.Paging.Page.CountsPart1
  type EncodeCountsCountsPart2 = _root_.com.histmersion.api.Paging.Page.CountsPart2
  val EncodeCountsCountsPart2: _root_.com.histmersion.api.Paging.Page.CountsPart2.type = _root_.com.histmersion.api.Paging.Page.CountsPart2
  type DecodeCountsResultPart1 = DecodeCountsResultPart1.T
  object DecodeCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeCountsResultPart2 = DecodeCountsResultPart2.T
  object DecodeCountsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeCountsS = DecodeCountsS.T
  object DecodeCountsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupedResult = GroupedResult.T
  object GroupedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupedN = GroupedN.T
  object GroupedN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingResult = HeadingResult.T
  object HeadingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingShown = HeadingShown.T
  object HeadingShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingTotal = _root_.com.histmersion.api.Paging.Page.Total
  val HeadingTotal: _root_.com.histmersion.api.Paging.Page.Total.type = _root_.com.histmersion.api.Paging.Page.Total
  type HeadingSingular = HeadingSingular.T
  object HeadingSingular extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingPlural = HeadingPlural.T
  object HeadingPlural extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadingQualifier = HeadingQualifier.T
  object HeadingQualifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderLineResult = OlderLineResult.T
  object OlderLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderLineAskArg1 = OlderLineAskArg1.T
  object OlderLineAskArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OlderLineAskResult = OlderLineAskResult.T
  object OlderLineAskResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutResultPart2 = CutResultPart2.T
  object CutResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CutLimit = CutLimit.T
  object CutLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  final case class Page(
      total: Page.Total,
      next: Option[Page.Next],
      counts: List[(Page.CountsPart1, Page.CountsPart2)],
      facets: List[(Page.FacetsPart1, List[(Page.FacetsPart2Part1, Page.FacetsPart2Part2)])] = Nil
  ):
    def hasMore: com.histmersion.api.Paging.Page.HasMoreResult = _root_.com.histmersion.api.Paging.Page.HasMoreResult(next.isDefined)
    def count(state: com.histmersion.api.Paging.Page.CountState): com.histmersion.api.Paging.Page.CountResult = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(0)

    def all: com.histmersion.api.Paging.Page.AllResult = counts.map(_._2).sum

    def facet(name: com.histmersion.api.Paging.Page.FacetName): List[(com.histmersion.api.Paging.Page.FacetResultPart1, com.histmersion.api.Paging.Page.FacetResultPart2)] = (facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)).map(refactorValue10 => { val refactorTuple7 = refactorValue10; (_root_.com.histmersion.api.Paging.Page.FacetResultPart1((refactorTuple7._1).value), _root_.com.histmersion.api.Paging.Page.FacetResultPart2((refactorTuple7._2).value)) })

  object Page:
    type HasMoreResult = HasMoreResult.T
    object HasMoreResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountState = CountState.T
    object CountState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllResult = AllResult.T
    object AllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetResultPart1 = FacetResultPart1.T
    object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetResultPart2 = FacetResultPart2.T
    object FacetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetName = FacetName.T
    object FacetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfCountsPart1 = OfCountsPart1.T
    object OfCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfCountsPart2 = OfCountsPart2.T
    object OfCountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfState = OfState.T
    object OfState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLast = _root_.com.histmersion.api.Paging.Page.FilteredLast
    val OfLast: _root_.com.histmersion.api.Paging.Page.FilteredLast.type = _root_.com.histmersion.api.Paging.Page.FilteredLast
    type FilteredCountsPart1 = _root_.com.histmersion.api.Paging.Page.CountsPart1
    val FilteredCountsPart1: _root_.com.histmersion.api.Paging.Page.CountsPart1.type = _root_.com.histmersion.api.Paging.Page.CountsPart1
    type FilteredCountsPart2 = _root_.com.histmersion.api.Paging.Page.CountsPart2
    val FilteredCountsPart2: _root_.com.histmersion.api.Paging.Page.CountsPart2.type = _root_.com.histmersion.api.Paging.Page.CountsPart2
    type FilteredStates = FilteredStates.T
    object FilteredStates extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilteredLast = FilteredLast.T
    object FilteredLast extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilteredFacetsPart1 = FilteredFacetsPart1.T
    object FilteredFacetsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilteredFacetsPart2Part1 = FilteredFacetsPart2Part1.T
    object FilteredFacetsPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilteredFacetsPart2Part2 = FilteredFacetsPart2Part2.T
    object FilteredFacetsPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Next = Next.T
    object Next extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountsPart1 = CountsPart1.T
    object CountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountsPart2 = CountsPart2.T
    object CountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetsPart1 = FacetsPart1.T
    object FacetsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetsPart2Part1 = FacetsPart2Part1.T
    object FacetsPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetsPart2Part2 = FacetsPart2Part2.T
    object FacetsPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val empty: Page = Page(_root_.com.histmersion.api.Paging.Page.Total(0), None, Nil)

    def of(counts: List[(com.histmersion.api.Paging.Page.OfCountsPart1, com.histmersion.api.Paging.Page.OfCountsPart2)], state: Option[com.histmersion.api.Paging.Page.OfState], last: Option[com.histmersion.api.Paging.Page.OfLast]): Page =
      filtered((counts).map(refactorValue2 => { val refactorTuple2 = refactorValue2; (_root_.com.histmersion.api.Paging.Page.CountsPart1((refactorTuple2._1).value), _root_.com.histmersion.api.Paging.Page.CountsPart2((refactorTuple2._2).value)) }), (state.toList).map(refactorValue12 => _root_.com.histmersion.api.Paging.Page.FilteredStates((refactorValue12).value)), last)

    def filtered(
        counts: List[(com.histmersion.api.Paging.Page.FilteredCountsPart1, com.histmersion.api.Paging.Page.FilteredCountsPart2)],
        states: List[com.histmersion.api.Paging.Page.FilteredStates],
        last: Option[com.histmersion.api.Paging.Page.FilteredLast],
        facets: List[(com.histmersion.api.Paging.Page.FilteredFacetsPart1, List[(com.histmersion.api.Paging.Page.FilteredFacetsPart2Part1, com.histmersion.api.Paging.Page.FilteredFacetsPart2Part2)])] = Nil
    ): Page =
      val total =
        if states.isEmpty then counts.map(_._2).sum else counts.collect { case (k, n) if states.contains(k) => n }.sum
      Page(total, (last).map(refactorValue4 => _root_.com.histmersion.api.Paging.Page.Next((refactorValue4).value)), counts, (facets.filter(_._2.nonEmpty)).map(refactorValue14 => { val refactorTuple9 = refactorValue14; (_root_.com.histmersion.api.Paging.Page.FacetsPart1((refactorTuple9._1).value), (refactorTuple9._2).map(refactorValue15 => { val refactorTuple10 = refactorValue15; (_root_.com.histmersion.api.Paging.Page.FacetsPart2Part1((refactorTuple10._1).value), _root_.com.histmersion.api.Paging.Page.FacetsPart2Part2((refactorTuple10._2).value)) })) }))

  def encodeCounts(counts: List[(com.histmersion.api.Paging.EncodeCountsCountsPart1, com.histmersion.api.Paging.EncodeCountsCountsPart2)]): com.histmersion.api.Paging.EncodeCountsResult =
    _root_.com.histmersion.api.Paging.EncodeCountsResult(counts.map((s, n) => s"$s=$n").mkString(","))

  def decodeCounts(s: com.histmersion.api.Paging.DecodeCountsS): List[(com.histmersion.api.Paging.DecodeCountsResultPart1, com.histmersion.api.Paging.DecodeCountsResultPart2)] =
    (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map({ val refactorTuple12 = k.trim -> _; (_root_.com.histmersion.api.Paging.DecodeCountsResultPart1(refactorTuple12._1), _root_.com.histmersion.api.Paging.DecodeCountsResultPart2(refactorTuple12._2)) })
        case _           => None
    }

  /** A server that predates these headers reads as "the page is the whole list". */
  val headers: EndpointIO[Page] =
    header[Option[Int]](TotalHeader)
      .and(header[Option[String]](NextHeader))
      .and(header[Option[String]](CountsHeader))
      .and(header[Option[String]](ListFilters.FacetsHeader))
      .map((t, n, c, f) =>
        Page(
          _root_.com.histmersion.api.Paging.Page.Total(t.getOrElse(0)),
          (n.map(_.trim).filter(_.nonEmpty)).map(refactorValue19 => _root_.com.histmersion.api.Paging.Page.Next(refactorValue19)),
          c.fold(Nil)(decodeCounts),
          f.fold(Nil)(ListFilters.decodeFacets)
        )
      )(p =>
        (
          Some(p.total),
          p.next,
          (Some(encodeCounts(p.counts)).filter((_).value.nonEmpty)).map(refactorValue8 => (refactorValue8).value),
          Some(ListFilters.encodeFacets(p.facets)).filter(_.nonEmpty)
        )
      )

  /** Grouped by hand, never through a locale: JDK 25's `en_PL` groups with a space. */
  def grouped(n: com.histmersion.api.Paging.GroupedN): com.histmersion.api.Paging.GroupedResult =
    val s = math.abs((n).value).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if (n).value < 0 then _root_.com.histmersion.api.Paging.GroupedResult(s"-$g") else _root_.com.histmersion.api.Paging.GroupedResult(g)

  def heading(shown: com.histmersion.api.Paging.HeadingShown, total: com.histmersion.api.Paging.HeadingTotal, singular: com.histmersion.api.Paging.HeadingSingular, plural: com.histmersion.api.Paging.HeadingPlural = _root_.com.histmersion.api.Paging.HeadingPlural(""), qualifier: Option[com.histmersion.api.Paging.HeadingQualifier] = None): com.histmersion.api.Paging.HeadingResult =
    val all = math.max((shown).value, (total).value)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(_root_.com.histmersion.api.Plural.NounN((all).toLong), singular, plural)
    if shown >= all then _root_.com.histmersion.api.Paging.HeadingResult(s"${grouped(_root_.com.histmersion.api.Paging.GroupedN((all).toLong))} $noun")
    else s"Latest ${grouped(shown)} of ${grouped(_root_.com.histmersion.api.Paging.GroupedN((all).toLong))} $noun"

  def olderLine(page: Page, ask: com.histmersion.api.Paging.OlderLineAskArg1 => com.histmersion.api.Paging.OlderLineAskResult): Option[com.histmersion.api.Paging.OlderLineResult] =
    page.next.map(c => s"Older: ${ask(c)}")

  def cut[A](rows: Vector[A], limit: com.histmersion.api.Paging.CutLimit): (Vector[A], com.histmersion.api.Paging.CutResultPart2) =
    if rows.size > (limit).value then (rows.take((limit).value), _root_.com.histmersion.api.Paging.CutResultPart2(true)) else (rows, _root_.com.histmersion.api.Paging.CutResultPart2(false))
