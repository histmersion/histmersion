package com.histmersion.domain.library.search

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.given
import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql
import com.histmersion.api.library.{Filter, Hit, SearchResult}
import com.histmersion.api.catalogue.Place

/** The filter's queries over sources: the search with its matched findings and passages, and the counts of what a
  * filter selects.
  */
private[search] object SearchRepo:

  /** How many sources the filter selects — `SearchResult.total` without building a page. */


  def count(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): com.histmersion.RowCount =
    (FilterSql.countQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0))

  /** How many findings the filter selects (uncapped — the map feed above stops at its limit). */
  /** How many findings matching `filter` landed in NO place at all — the "unlocated" bucket of the region tree (plan
    * §1.6a). Built from the SHARED [[FilterSql]] fragments, so it is narrowed by exactly the same criteria as the map
    * feed.
    */
  def unlocated(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): com.histmersion.RowCount =
    val q = Sql.raw("SELECT count(*)::int FROM findings f JOIN sources s ON s.citekey = f.citekey WHERE ") ++
      FilterSql.sourceWhere(filter) ++ Sql.raw(" AND ") ++ FilterSql.findingWhere(filter, scope = scope) ++
      Sql.raw(" AND NOT EXISTS (SELECT 1 FROM finding_places fp WHERE fp.finding_id = f.id)")
    (q.frag.query[Int].run().headOption.getOrElse(0))

  /** How many sources, findings, articles and events the filter selects ([[FilterSql.selectionCountsQuery]]). */
  def selectionCounts(filter: Filter, scope: PlaceScope = PlaceScope.empty)(using DbCon): Place.Counts =
    (FilterSql.selectionCountsQuery(filter, scope).frag.query[(Int, Int, Int, Int)].run().head match
      case (s, f, a, e) => Place.Counts(s, f, a, e)
    )

  /** THE search (plan §1.0/§1.3/§1.4): the sources matching the filter, each with the findings that made it match, why
    * it matched, and — for a text query — the passage that matched (`snippet` + `matchedHeading`, from the best-scoring
    * body chunk). `total` is count(DISTINCT source) for the whole filter, ignoring limit/offset. `filter.limit` must
    * already be resolved against the configured default, and `snippetWords` is `limits.snippetWords` (the handler does
    * both).
    *
    * Two-phase FTS: `sourcesQuery` uses a CTE to rank sources (one scan of `source_chunks`), then `snippetsQuery`
    * computes `ts_headline` only for the page's citekeys — typically 10–25 instead of every matching source in the
    * corpus.
    */
  def sources(filter: Filter, snippetWords: com.histmersion.domain.library.search.Search.SnippetWords, scope: PlaceScope = PlaceScope.empty)(using DbCon): SearchResult =
    val total = FilterSql.countQuery(filter, scope).frag.query[Int].run().headOption.getOrElse(0)
    val rows = FilterSql.sourcesQuery(filter, snippetWords, scope).frag.query[FilterSql.SourceRow].run()
    if rows.isEmpty then SearchResult.of(total, Nil, filter.offset.max(0))
    else
      val citekeys = rows.map(_.citekey).toList
      // phase 2: snippets for the page only (the expensive ts_headline runs for N page rows, not all)
      val snippets: Map[com.histmersion.domain.library.Citekey, FilterSql.SnippetRow] =
        FilterSql.text(filter) match
          case Some(q) =>
            FilterSql
              .snippetsQuery(q, snippetWords, citekeys)
              .frag
              .query[FilterSql.SnippetRow]
              .run()
              .map(s => s.citekey -> s)
              .toMap
          case None => Map.empty
      // one round-trip for the page's findings; which ones count as evidence is decided purely
      val byCk = FilterSql
        .findingsQuery(citekeys)
        .frag
        .query[FindingHitRow]
        .run()
        .groupBy(_.citekey)
      val hits = rows.map { r =>
        // merge the snippet into the row before building the hit
        val withSnippet = snippets.get(r.citekey) match
          case Some(sr) => r.modify(_.snippet).setTo(Some(sr.snippet)).modify(_.matchedHeading).setTo(sr.heading)
          case None     => r
        val matched =
          if !filter.hasFindingCriteria then Nil
          else
            byCk.getOrElse(r.citekey, Vector.empty).toList.flatMap { fr =>
              val m = fr.toMatch
              val why = FilterSql.findingWhy(filter, m, fr.assignedPlaceIds, scope)
              if why.isEmpty then None else Some(m.modify(_.why).setTo(why))
            }
        Hit(withSnippet.toHit, matched, FilterSql.sourceWhy(filter, withSnippet))
      }
      SearchResult.of(total, hits.toList, filter.offset.max(0))
