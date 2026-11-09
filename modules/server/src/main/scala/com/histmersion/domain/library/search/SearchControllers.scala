package com.histmersion
package domain.library.search

import com.histmersion.api.Problem
import com.histmersion.api.library.Tree

import com.histmersion.api.Api
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.{PlaceScope, PlaceService}

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.{Aggregate, Filter, SearchHit, SearchResult, TimeHistogram}
import com.histmersion.api.catalogue.{Place, RegionNode}
import com.histmersion.api.catalogue.Year

/** The search over REST and MCP: the sources a filter selects with their matched findings and why, the histogram and
  * its nested tree, and the counts of what a filter selects. The gazetteer (`places`) is the catalogue's: the tree's
  * region levels hang off it.
  */
final class SearchControllers(
    search: SearchService,
    filters: SearchFilters,
    places: PlaceService,
    cfg: () => AppConfig
):

  /** Legacy `GET /api/search` — kept working (the web page uses it) by delegating to the filtered search and dropping
    * the evidence, so there is ONE query path (FTS ranking and snippets included). New callers should use [[sources]].
    */
  def search(q: Option[Search.Query], limit: Option[Filter.Limit]): List[SearchHit] =
    sources(
      Filter(q = q.map(_.value.trim).filter(_.nonEmpty).map(Filter.Q(_)), limit = limit.getOrElse(Filter.Limit(0)))
    ).hits.map(_.source)

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult =
    search.sources(filters.page(f), Search.SnippetWords(cfg().limits.snippetWords.value), filters.scope(f))

  /** How many sources, findings, articles and events `f` selects. */
  def selectionCounts(f: Filter): Place.Counts = search.selectionCounts(f, filters.scope(f))

  /** `/api/aggregate` — the timeline strip (§1.7) AND the nested tree (§1.6). Narrowed by the SAME filter as
    * `/api/sources` and `/api/findings`; the window defaults to the configured UI span.
    *
    * `by=time` alone keeps the wave-1 behaviour byte for byte: the flat bars, gaps included, which is what the strip
    * draws. Adding `depth`/`samples` nests the same numbers, and `by=region|time,region|region,time` fills `tree`
    * instead. SQL only ever SELECTS rows; [[Aggregate]] and [[Tree]] (both pure) do all the bucketing, so the strip,
    * the tabs, the CLI and MCP cannot drift apart.
    *
    * Deeper levels are LAZY by construction: there is no cursor — a client narrows `from`/`to` (or `place`) to one
    * bucket and asks again, which is the same query with a smaller window.
    */
  def aggregate(q: Api.AggregateQuery): Either[Api.Error, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val unit = norm(q.unit.map(_.value), "sources")
    val by = norm(q.by.map(_.value), Tree.KindTime)
    Tree
      .parseBy(by)
      .toRight(
        error(
          Problem(s"unsupported by='$by' — use " +
            "time | region | time,region | region,time")
        )
      )
      .flatMap { axes =>
        if unit != "sources" && unit != "findings" && unit != Aggregate.UnitStarts then
          Left(error(Problem(s"unsupported unit='$unit' — use sources, findings or starts")))
        else aggregateOn(axes, unit, q)
      }

  private def error(message: Problem): Api.Error = Api.Error(message)

  /** The aggregation itself, once `by`/`unit` have been validated.
    *
    * The window is CLAMPED to the configured deep-time span (`ui.time.min/max`) and the resulting bucket count is
    * capped (`ui.max-buckets`). Without either, `from=-1e15&to=1e15` asked for 400 000 000 000 buckets: the request
    * built them one `TimeBucket` at a time until the heap gave out, and `to = Long.MaxValue` overflowed the bucket
    * arithmetic on top of that.
    */
  private def aggregateOn(axes: List[String], unit: String, q: Api.AggregateQuery): Either[Api.Error, TimeHistogram] =
    val ui = cfg().ui
    val t = ui.time
    // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
    val a = Filter.lowerGiven(q.filter.period).getOrElse(Year(t.defaultFrom.value))
    val b = Filter.upperGiven(q.filter.period).getOrElse(Year(t.defaultTo.value))
    val (lo, hi) = if a <= b then (a, b) else (b, a)
    val (from, to) = (clampToSpan(lo, t.min.value, t.max.value), clampToSpan(hi, t.min.value, t.max.value))
    val size = q.size
      .map(_.value)
      .filter(_ > 0)
      .fold(Aggregate.bucketSize(Aggregate.Span(to.value - from.value)))(n => Aggregate.Width(n))
    val wanted = Aggregate.bucketCount(from, to, size)
    if wanted > BigInt(math.max(1, ui.maxBuckets.value)) then
      Left(
        error(
          Problem(s"that window needs $wanted buckets of ${size.value} year(s), over the " +
            s"${ui.maxBuckets.value} allowed (ui.max-buckets) — narrow `from`/`to`")
        )
      )
    else Right(aggregateWindow(axes, unit, q, from, to, size))

  /** A caller's year, forced inside the configured span. `ui.time.min/max` is what the web UI can draw and what the
    * corpus covers; nothing outside it can have a non-empty answer anyway.
    */
  private def clampToSpan(v: Year, min: Long, max: Long): Year = Year(math.max(min, math.min(max, v.value)))

  private def aggregateWindow(
      axes: List[String],
      unit: String,
      q: Api.AggregateQuery,
      from: Year,
      to: Year,
      size: Aggregate.Width
  ): TimeHistogram =
    val ui = cfg().ui
    val window = q.filter.modify(_.period).setTo(Some(Period(from, to)))
    val scope = filters.scope(window)
    val depth =
      Tree.Depth(q.depth.map(_.value).filter(_ > 0).getOrElse(ui.treeDepth.value).max(1).min(Tree.MaxDepth.value))
    val samples =
      Tree.MaxSamplesCount(q.samples.map(_.value).getOrElse(ui.treeSamples.value).max(0).min(Tree.MaxSamples.value))
    // `unit=starts` is the SOURCE unit counted a different way (plan §1.7): same rows, one bar each,
    // by where the period begins. The rows query therefore still asks for sources.
    val starts = unit == Aggregate.UnitStarts
    val rowUnit = if starts then "sources" else unit
    val counting = if starts then Aggregate.Counting.Starts else Aggregate.Counting.Overlap
    val flat =
      if axes.headOption.contains(Tree.KindTime) then
        Aggregate.histogram(search.periods(AggregateRepo.Unit(rowUnit), window, scope), from, to, size, counting)
      else Nil
    val base = TimeHistogram(
      TimeHistogram.From(from.value),
      TimeHistogram.To(to.value),
      TimeHistogram.Bucket(size.value),
      flat,
      by = TimeHistogram.By(axes.mkString(",")),
      unit = TimeHistogram.Unit(unit),
      depth = TimeHistogram.Depth(depth.value)
    )
    // The TIMELINE STRIP asks for `by=time` on every span change and only wants `buckets`; building
    // the tree as well would double its cost for nothing. A caller that wants the nested answer for
    // the same axis says so by asking for a `depth`/`samples` — which is exactly what the "by time"
    // tab does, and what the CLI's `--group time` sends.
    if axes == List(Tree.KindTime) && q.depth.isEmpty && q.samples.isEmpty then base
    else treeOn(axes, rowUnit, window, scope, from, to, depth, samples, q.asOf.map(a => Year(a.value)), base)

  /** The nested part of [[aggregateOn]] — split out so the flat strip never pays for it. */
  private def treeOn(
      axes: List[String],
      unit: String,
      window: Filter,
      scope: PlaceScope,
      from: Year,
      to: Year,
      depth: Tree.Depth,
      samples: Tree.MaxSamplesCount,
      askedAsOf: Option[Year],
      base: TimeHistogram
  ): TimeHistogram =
    val rows = search.treeRows(AggregateRepo.Unit(unit), window, scope)
    // the gazetteer slice these rows hang off — one upward closure, not the whole 16 k-place table
    def gaz =
      places.gazetteer(rows.flatMap(_.placeIds.map(p => com.histmersion.domain.catalogue.Place.Id.assume(p.value))))
    // `by=region` reads the hierarchy at ONE year: the caller's, else the middle of the window
    val asOf = askedAsOf.orElse(Some(Tree.midpoint(from, to)))
    // LAZY DEEPER LEVELS: `place=<one gazetteer id>` roots the region tree AT that place, so
    // expanding a node returns its subtree rather than the whole forest with the node buried in it.
    // (Two place chips, or a name that is not a gazetteer id, is an ordinary filter — no re-rooting.)
    val root = window.places.filter(p => scope.idsFor(PlaceScope.Value(p.value)).isDefined) match
      case one :: Nil => Some(RegionNode.Id(one.value))
      case _          => None
    val tree = axes match
      case Tree.KindTime :: Nil   => Tree.timeTree(rows, from, to, depth, samples)
      case Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.regionTree(rows, ns, rs, asOf, depth, samples, root)
      case Tree.KindTime :: Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.timeRegionTree(rows, from, to, ns, rs, depth, samples)
      case _ =>
        val (ns, rs) = gaz; Tree.regionTimeTree(rows, from, to, ns, rs, asOf, depth, samples, root)
    base
      .modify(_.total)
      .setTo(TimeHistogram.Total(rows.iterator.map(_.key).toSet.size))
      // `time,region` reads the gazetteer at each bucket's OWN midpoint, so no single asOf applies
      .modify(_.asOf)
      .setTo(if axes == List(Tree.KindTime, Tree.KindRegion) || axes == List(Tree.KindTime) then None else asOf)
      .modify(_.tree)
      .setTo(tree)
