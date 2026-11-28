package com.histmersion
package api

import sttp.tapir.*
import com.histmersion.api.automation.ListFilters
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Facet
import com.histmersion.api.library.Filter
import com.histmersion.api.automation.Activity
import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.library.TimeHistogram

/** Paging travels in response headers so the JSON body stays the plain array older CLIs and MCP clients read. */
@movesTo(FeaturePlace.Kernel)
object Paging:
  object OlderLine:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  type Cursor = Cursor.T
  object Cursor extends Newtype[String, Pure]
  type GroupSize = GroupSize.T
  object GroupSize extends Newtype[Long, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Shown = Shown.T
  object Shown extends Newtype[Int, Pure]
  type ShownCount = ShownCount.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object ShownCount extends Newtype[Int, Pure]

  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  final case class Page(
      total: Page.Total,
      next: Option[Page.Next],
      counts: List[(Page.CountState, Count)],
      facets: List[(Facet.Name, List[(Facet.Value, Facet.Count)])] = Nil
  ):
    def hasMore: Boolean = (next.isDefined)
    def count(state: Page.CountState): Count =
      counts.collectFirst { case (s, n) if s == state => n }.getOrElse(Count(0))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def all: Page.Total = Page.Total(counts.map(_._2.value).sum)

    def facet(name: Facet.Name): List[(Facet.Value, Facet.Count)] =
      facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)

  object Page:
    type CountState = CountState.T
    object CountState extends Newtype[String, Pure]
    export TimeHistogram.Total
    export Paging.Cursor as Next

    val empty: Page = Page(Total(0), None, Nil)

    def of(counts: List[(CountState, Count)], state: Option[CountState], last: Option[Filter.Last]): Page =
      filtered(counts, state.toList, last)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def filtered(
        counts: List[(CountState, Count)],
        states: List[CountState],
        last: Option[Filter.Last],
        facets: List[(Facet.Name, List[(Facet.Value, Facet.Count)])] = Nil
    ): Page =
      val total =
        Total(if states.isEmpty then counts.map(_._2.value).sum
        else counts.collect { case (k, n) if states.contains(k) => n.value }.sum)
      Page(total, (last).map(refactorValue4 => Cursor((refactorValue4).value)), counts, facets.filter(_._2.nonEmpty))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def encodeCounts(counts: List[(Page.CountState, Count)]): Activity.Counts.Wire =
    Activity.Counts.Wire(counts.map((s, n) => s"$s=$n").mkString(","))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decodeCounts(s: Activity.Counts.Wire): List[(Page.CountState, Count)] =
    (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map(c => (Page.CountState(k.trim), Count(c)))
        case _           => None
    }

  /** A server that predates these headers reads as "the page is the whole list". */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val headers: EndpointIO[Page] =
    header[Option[Int]](TotalHeader)
      .and(header[Option[String]](NextHeader))
      .and(header[Option[String]](CountsHeader))
      .and(header[Option[String]](ListFilters.FacetsHeader))
      .map((t, n, c, f) =>
        Page(
          Page.Total(t.getOrElse(0)),
          (n.map(_.trim).filter(_.nonEmpty)).map(refactorValue19 => Cursor(refactorValue19)),
          c.fold[List[(Page.CountState, Count)]](Nil)(h => decodeCounts(Activity.Counts.Wire(h))),
          f.fold[List[(Facet.Name, List[(Facet.Value, Facet.Count)])]](Nil)(h =>
            ListFilters
              .decodeFacets(ListFilters.EncodedFacets(h))
              .map((name, vs) =>
                (
                  Facet.Name(name.value),
                  vs.map((term, n) => (term, Facet.Count(n.value)))
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
                  vs.map((v, n) => (ListFilters.Term(v.value), RowCount(n.value)))
                )
              )
            )
          ).filter(_.value.nonEmpty).map(_.value)
        )
      )

  /** Grouped by hand, never through a locale: JDK 25's `en_PL` groups with a space. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def grouped(n: GroupSize): CatalogRecord.Group.Label =
    val s = math.abs((n).value).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if (n).value < 0 then CatalogRecord.Group.Label(s"-$g") else CatalogRecord.Group.Label(g)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def heading(
      shown: ShownCount,
      total: Page.Total,
      singular: Plural.Noun.Singular,
      plural: Plural.Noun.Plural = Plural.Noun.Plural(""),
      qualifier: Option[String] = None
  ): Api.SourceViewQuery.Heading.Text =
    val all = math.max((shown).value, (total).value)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(Plural.Count((all).toLong), singular, plural)
    if shown.value >= all then Api.SourceViewQuery.Heading.Text(s"${grouped(GroupSize((all).toLong))} $noun")
    else
      Api.SourceViewQuery.Heading.Text(
        s"Latest ${grouped(GroupSize(shown.value.toLong))} of ${grouped(GroupSize((all).toLong))} $noun"
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def olderLine(page: Page, ask: Cursor => OlderLine.Text): Option[OlderLine.Text] =
    page.next.map(c => OlderLine.Text(s"Older: ${ask(c)}"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cut[A](rows: Vector[A], limit: Limit): (Vector[A], Boolean) =
    if rows.size > (limit).value then (rows.take((limit).value), (true)) else (rows, (false))
