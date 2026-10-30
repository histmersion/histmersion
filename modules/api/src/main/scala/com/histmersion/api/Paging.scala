package com.histmersion.api

import sttp.tapir.*

/** Paging travels in response headers so the JSON body stays the plain array older CLIs and MCP clients read. */
object Paging:

  val TotalHeader = "X-Total-Count"
  val NextHeader = "X-Next-Before"
  val CountsHeader = "X-State-Counts"

  final case class Page(
      total: Int,
      next: Option[String],
      counts: List[(String, Int)],
      facets: List[(String, List[(String, Int)])] = Nil
  ):
    def hasMore: Boolean = next.isDefined
    def count(state: String): Int = counts.collectFirst { case (s, n) if s == state => n }.getOrElse(0)

    def all: Int = counts.map(_._2).sum

    def facet(name: String): List[(String, Int)] = facets.collectFirst { case (`name`, vs) => vs }.getOrElse(Nil)

  object Page:
    val empty: Page = Page(0, None, Nil)

    def of(counts: List[(String, Int)], state: Option[String], last: Option[String]): Page =
      filtered(counts, state.toList, last)

    def filtered(
        counts: List[(String, Int)],
        states: List[String],
        last: Option[String],
        facets: List[(String, List[(String, Int)])] = Nil
    ): Page =
      val total =
        if states.isEmpty then counts.map(_._2).sum else counts.collect { case (k, n) if states.contains(k) => n }.sum
      Page(total, last, counts, facets.filter(_._2.nonEmpty))

  def encodeCounts(counts: List[(String, Int)]): String =
    counts.map((s, n) => s"$s=$n").mkString(",")

  def decodeCounts(s: String): List[(String, Int)] =
    s.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { pair =>
      pair.split('=') match
        case Array(k, v) => v.trim.toIntOption.map(k.trim -> _)
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
          t.getOrElse(0),
          n.map(_.trim).filter(_.nonEmpty),
          c.fold(Nil)(decodeCounts),
          f.fold(Nil)(ListFilters.decodeFacets)
        )
      )(p =>
        (
          Some(p.total),
          p.next,
          Some(encodeCounts(p.counts)).filter(_.nonEmpty),
          Some(ListFilters.encodeFacets(p.facets)).filter(_.nonEmpty)
        )
      )

  /** Grouped by hand, never through a locale: JDK 25's `en_PL` groups with a space. */
  def grouped(n: Long): String =
    val s = math.abs(n).toString
    val g = s.reverse.grouped(3).mkString(",").reverse
    if n < 0 then s"-$g" else g

  def heading(shown: Int, total: Int, singular: String, plural: String = "", qualifier: Option[String] = None): String =
    val all = math.max(shown, total)
    val noun = qualifier.fold("")(q => s"$q ") + Plural.noun(all, singular, plural)
    if shown >= all then s"${grouped(all)} $noun"
    else s"Latest ${grouped(shown)} of ${grouped(all)} $noun"

  def olderLine(page: Page, ask: String => String): Option[String] =
    page.next.map(c => s"Older: ${ask(c)}")

  def cut[A](rows: Vector[A], limit: Int): (Vector[A], Boolean) =
    if rows.size > limit then (rows.take(limit), true) else (rows, false)
