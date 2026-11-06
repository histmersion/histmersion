package com.histmersion.api

import sttp.tapir.*
import com.histmersion.api.automation.ListFilters

/** Paging travels in response headers so the JSON body stays the plain array older CLIs and MCP clients read. */
object Paging:
  object OlderLine:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cursor = Cursor.T
  object Cursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GroupSize = GroupSize.T
  object GroupSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Shown = Shown.T
  object Shown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShownCount = ShownCount.T
  object ShownCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type EncodeCountsCountsPart1 = _root_.com.histmersion.api.Paging.Page.CountsPart1
  val EncodeCountsCountsPart1: _root_.com.histmersion.api.Paging.Page.CountsPart1.type = _root_.com.histmersion.api.Paging.Page.CountsPart1
  type EncodeCountsCountsPart2 = _root_.com.histmersion.api.Paging.Count
  val EncodeCountsCountsPart2: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
  type DecodeCountsResultPart1 = DecodeCountsResultPart1.T
  object DecodeCountsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeCountsResultPart2 = com.histmersion.api.Paging.Count
  val DecodeCountsResultPart2: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
  type HeadingTotal = _root_.com.histmersion.api.Paging.Page.Total
  val HeadingTotal: _root_.com.histmersion.api.Paging.Page.Total.type = _root_.com.histmersion.api.Paging.Page.Total

  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  final case class Page(
      total: Page.Total,
      next: Option[Page.Next],
      counts: List[(Page.CountsPart1, Page.CountsPart2)],
      facets: List[(Page.FacetsPart1, List[(Page.FacetsPart2Part1, Page.FacetsPart2Part2)])] = Nil
  ):
    def hasMore: Boolean = (next.isDefined)
    def count(state: com.histmersion.api.Paging.Page.CountState): com.histmersion.api.Paging.Count = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(0)

    def all: com.histmersion.api.Paging.Page.Total = counts.map(_._2).sum

    def facet(name: com.histmersion.api.library.Facet.Name): List[(com.histmersion.api.Paging.Page.FacetResultPart1, com.histmersion.api.library.Facet.Count)] = (facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)).map(refactorValue10 => { val refactorTuple7 = refactorValue10; (_root_.com.histmersion.api.Paging.Page.FacetResultPart1((refactorTuple7._1).value), _root_.com.histmersion.api.library.Facet.Count((refactorTuple7._2).value)) })

  object Page:
    type CountResult = com.histmersion.api.Paging.Count
    val CountResult: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
    type CountState = CountState.T
    object CountState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetResultPart1 = FacetResultPart1.T
    object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfCountsPart1 = OfCountsPart1.T
    object OfCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfCountsPart2 = com.histmersion.api.Paging.Count
    val OfCountsPart2: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
    type OfState = OfState.T
    object OfState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLast = _root_.com.histmersion.api.library.Filter.Last
    val OfLast: _root_.com.histmersion.api.library.Filter.Last.type = _root_.com.histmersion.api.library.Filter.Last
    type FilteredCountsPart1 = _root_.com.histmersion.api.Paging.Page.CountsPart1
    val FilteredCountsPart1: _root_.com.histmersion.api.Paging.Page.CountsPart1.type = _root_.com.histmersion.api.Paging.Page.CountsPart1
    type FilteredCountsPart2 = _root_.com.histmersion.api.Paging.Count
    val FilteredCountsPart2: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
    type FilteredStates = FilteredStates.T
    object FilteredStates extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FilteredFacetsPart2Part1 = FilteredFacetsPart2Part1.T
    object FilteredFacetsPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Next = com.histmersion.api.Paging.Cursor
    val Next: _root_.com.histmersion.api.Paging.Cursor.type = _root_.com.histmersion.api.Paging.Cursor
    type CountsPart1 = CountsPart1.T
    object CountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountsPart2 = com.histmersion.api.Paging.Count
    val CountsPart2: _root_.com.histmersion.api.Paging.Count.type = _root_.com.histmersion.api.Paging.Count
    type FacetsPart1 = com.histmersion.api.library.Facet.Name
    val FacetsPart1: _root_.com.histmersion.api.library.Facet.Name.type = _root_.com.histmersion.api.library.Facet.Name
    type FacetsPart2Part1 = FacetsPart2Part1.T
    object FacetsPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetsPart2Part2 = com.histmersion.api.library.Facet.Count
    val FacetsPart2Part2: _root_.com.histmersion.api.library.Facet.Count.type = _root_.com.histmersion.api.library.Facet.Count

    val empty: Page = Page(_root_.com.histmersion.api.Paging.Page.Total(0), None, Nil)

    def of(counts: List[(com.histmersion.api.Paging.Page.OfCountsPart1, com.histmersion.api.Paging.Count)], state: Option[com.histmersion.api.Paging.Page.OfState], last: Option[com.histmersion.api.Paging.Page.OfLast]): Page =
      filtered((counts).map(refactorValue2 => { val refactorTuple2 = refactorValue2; (_root_.com.histmersion.api.Paging.Page.CountsPart1((refactorTuple2._1).value), _root_.com.histmersion.api.Paging.Count((refactorTuple2._2).value)) }), (state.toList).map(refactorValue12 => _root_.com.histmersion.api.Paging.Page.FilteredStates((refactorValue12).value)), last)

    def filtered(
        counts: List[(com.histmersion.api.Paging.Page.FilteredCountsPart1, com.histmersion.api.Paging.Page.FilteredCountsPart2)],
        states: List[com.histmersion.api.Paging.Page.FilteredStates],
        last: Option[com.histmersion.api.library.Filter.Last],
        facets: List[(com.histmersion.api.library.Facet.Name, List[(com.histmersion.api.Paging.Page.FilteredFacetsPart2Part1, com.histmersion.api.library.Facet.Count)])] = Nil
    ): Page =
      val total =
        if states.isEmpty then counts.map(_._2).sum else counts.collect { case (k, n) if states.contains(k) => n }.sum
      Page(total, (last).map(refactorValue4 => _root_.com.histmersion.api.Paging.Cursor((refactorValue4).value)), counts, (facets.filter(_._2.nonEmpty)).map(refactorValue14 => { val refactorTuple9 = refactorValue14; (_root_.com.histmersion.api.library.Facet.Name((refactorTuple9._1).value), (refactorTuple9._2).map(refactorValue15 => { val refactorTuple10 = refactorValue15; (_root_.com.histmersion.api.Paging.Page.FacetsPart2Part1((refactorTuple10._1).value), _root_.com.histmersion.api.library.Facet.Count((refactorTuple10._2).value)) })) }))

  def encodeCounts(counts: List[(com.histmersion.api.Paging.EncodeCountsCountsPart1, com.histmersion.api.Paging.EncodeCountsCountsPart2)]): com.histmersion.api.automation.Activity.Counts.Wire =
    _root_.com.histmersion.api.automation.Activity.Counts.Wire(counts.map((s, n) => s"$s=$n").mkString(","))

  def decodeCounts(s: com.histmersion.api.automation.Activity.Counts.Wire): List[(com.histmersion.api.Paging.DecodeCountsResultPart1, com.histmersion.api.Paging.Count)] =
    (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map({ val refactorTuple12 = k.trim -> _; (_root_.com.histmersion.api.Paging.DecodeCountsResultPart1(refactorTuple12._1), _root_.com.histmersion.api.Paging.Count(refactorTuple12._2)) })
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
          (n.map(_.trim).filter(_.nonEmpty)).map(refactorValue19 => _root_.com.histmersion.api.Paging.Cursor(refactorValue19)),
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
  def grouped(n: com.histmersion.api.Paging.GroupSize): com.histmersion.api.catalogue.CatalogRecord.Group.Label =
    val s = math.abs((n).value).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if (n).value < 0 then _root_.com.histmersion.api.catalogue.CatalogRecord.Group.Label(s"-$g") else _root_.com.histmersion.api.catalogue.CatalogRecord.Group.Label(g)

  def heading(shown: com.histmersion.api.Paging.ShownCount, total: com.histmersion.api.Paging.HeadingTotal, singular: com.histmersion.api.Plural.Noun.Singular, plural: com.histmersion.api.Plural.Noun.Plural = _root_.com.histmersion.api.Plural.Noun.Plural(""), qualifier: Option[String] = None): com.histmersion.api.Api.SourceViewQuery.Heading.Text =
    val all = math.max((shown).value, (total).value)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(_root_.com.histmersion.api.Plural.Count((all).toLong), singular, plural)
    if shown >= all then _root_.com.histmersion.api.Api.SourceViewQuery.Heading.Text(s"${grouped(_root_.com.histmersion.api.Paging.GroupSize((all).toLong))} $noun")
    else s"Latest ${grouped(shown)} of ${grouped(_root_.com.histmersion.api.Paging.GroupSize((all).toLong))} $noun"

  def olderLine(page: Page, ask: com.histmersion.api.Paging.OlderLine.Key => com.histmersion.api.Paging.OlderLine.Text): Option[com.histmersion.api.Paging.OlderLine.Text] =
    page.next.map(c => s"Older: ${ask(c)}")

  def cut[A](rows: Vector[A], limit: com.histmersion.api.Paging.Limit): (Vector[A], Boolean) =
    if rows.size > (limit).value then (rows.take((limit).value), (true)) else (rows, (false))
