package com.histmersion.api

import sttp.tapir.*
import com.histmersion.api.automation.ListFilters

/** Paging travels in response headers so the JSON body stays the plain array older CLIs and MCP clients read. */
object Paging:
  object OlderLine:
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


  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  final case class Page(
      total: Page.Total,
      next: Option[Page.Next],
      counts: List[(Page.CountState, Count)],
      facets: List[(com.histmersion.api.library.Facet.Name, List[(com.histmersion.api.library.Facet.Value, com.histmersion.api.library.Facet.Count)])] = Nil
  ):
    def hasMore: Boolean = (next.isDefined)
    def count(state: com.histmersion.api.Paging.Page.CountState): com.histmersion.api.Paging.Count = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(Count(0))

    def all: com.histmersion.api.Paging.Page.Total = Page.Total(counts.map(_._2.value).sum)

    def facet(name: com.histmersion.api.library.Facet.Name): List[(com.histmersion.api.library.Facet.Value, com.histmersion.api.library.Facet.Count)] = facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)

  object Page:
    type CountState = CountState.T
    object CountState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Next = com.histmersion.api.Paging.Cursor
    val Next: _root_.com.histmersion.api.Paging.Cursor.type = _root_.com.histmersion.api.Paging.Cursor

    val empty: Page = Page(_root_.com.histmersion.api.Paging.Page.Total(0), None, Nil)

    def of(counts: List[(com.histmersion.api.Paging.Page.CountState, com.histmersion.api.Paging.Count)], state: Option[com.histmersion.api.Paging.Page.CountState], last: Option[com.histmersion.api.library.Filter.Last]): Page =
      filtered(counts, state.toList, last)

    def filtered(
        counts: List[(com.histmersion.api.Paging.Page.CountState, com.histmersion.api.Paging.Count)],
        states: List[com.histmersion.api.Paging.Page.CountState],
        last: Option[com.histmersion.api.library.Filter.Last],
        facets: List[(com.histmersion.api.library.Facet.Name, List[(com.histmersion.api.library.Facet.Value, com.histmersion.api.library.Facet.Count)])] = Nil
    ): Page =
      val total =
        Total(if states.isEmpty then counts.map(_._2.value).sum else counts.collect { case (k, n) if states.contains(k) => n.value }.sum)
      Page(total, (last).map(refactorValue4 => _root_.com.histmersion.api.Paging.Cursor((refactorValue4).value)), counts, facets.filter(_._2.nonEmpty))

  def encodeCounts(counts: List[(com.histmersion.api.Paging.Page.CountState, com.histmersion.api.Paging.Count)]): com.histmersion.api.automation.Activity.Counts.Wire =
    _root_.com.histmersion.api.automation.Activity.Counts.Wire(counts.map((s, n) => s"$s=$n").mkString(","))

  def decodeCounts(s: com.histmersion.api.automation.Activity.Counts.Wire): List[(com.histmersion.api.Paging.Page.CountState, com.histmersion.api.Paging.Count)] =
    (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map(c => (Page.CountState(k.trim), Count(c)))
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
          c.fold[List[(Page.CountState, Count)]](Nil)(h => decodeCounts(_root_.com.histmersion.api.automation.Activity.Counts.Wire(h))),
          f.fold[List[(com.histmersion.api.library.Facet.Name, List[(com.histmersion.api.library.Facet.Value, com.histmersion.api.library.Facet.Count)])]](Nil)(h =>
            ListFilters
              .decodeFacets(ListFilters.EncodedFacets(h))
              .map((name, vs) =>
                (
                  com.histmersion.api.library.Facet.Name(name.value),
                  vs.map((term, n) => (com.histmersion.api.library.Facet.Value(term.value), com.histmersion.api.library.Facet.Count(n.value)))
                )
              )
          )
        )
      )(p =>
        (
          Some(p.total.value),
          p.next.map(_.value),
          (Some(encodeCounts(p.counts)).filter((_).value.nonEmpty)).map(refactorValue8 => (refactorValue8).value),
          Some(
            ListFilters.encodeFacets(
              p.facets.map((name, vs) =>
                (
                  ListFilters.FacetName(name.value),
                  vs.map((v, n) => (ListFilters.Term(v.value), com.histmersion.api.RowCount(n.value)))
                )
              )
            )
          ).filter(_.value.nonEmpty).map(_.value)
        )
      )

  /** Grouped by hand, never through a locale: JDK 25's `en_PL` groups with a space. */
  def grouped(n: com.histmersion.api.Paging.GroupSize): com.histmersion.api.catalogue.CatalogRecord.Group.Label =
    val s = math.abs((n).value).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if (n).value < 0 then _root_.com.histmersion.api.catalogue.CatalogRecord.Group.Label(s"-$g") else _root_.com.histmersion.api.catalogue.CatalogRecord.Group.Label(g)

  def heading(shown: com.histmersion.api.Paging.ShownCount, total: com.histmersion.api.Paging.Page.Total, singular: com.histmersion.api.Plural.Noun.Singular, plural: com.histmersion.api.Plural.Noun.Plural = _root_.com.histmersion.api.Plural.Noun.Plural(""), qualifier: Option[String] = None): com.histmersion.api.Api.SourceViewQuery.Heading.Text =
    val all = math.max((shown).value, (total).value)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(_root_.com.histmersion.api.Plural.Count((all).toLong), singular, plural)
    if shown.value >= all then _root_.com.histmersion.api.Api.SourceViewQuery.Heading.Text(s"${grouped(_root_.com.histmersion.api.Paging.GroupSize((all).toLong))} $noun")
    else _root_.com.histmersion.api.Api.SourceViewQuery.Heading.Text(s"Latest ${grouped(_root_.com.histmersion.api.Paging.GroupSize(shown.value.toLong))} of ${grouped(_root_.com.histmersion.api.Paging.GroupSize((all).toLong))} $noun")

  def olderLine(page: Page, ask: com.histmersion.api.Paging.Cursor => com.histmersion.api.Paging.OlderLine.Text): Option[com.histmersion.api.Paging.OlderLine.Text] =
    page.next.map(c => _root_.com.histmersion.api.Paging.OlderLine.Text(s"Older: ${ask(c)}"))

  def cut[A](rows: Vector[A], limit: com.histmersion.api.Paging.Limit): (Vector[A], Boolean) =
    if rows.size > (limit).value then (rows.take((limit).value), (true)) else (rows, (false))
