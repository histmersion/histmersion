package com.histmersion.domain.library.search

import com.histmersion.api.library.search.Tree

import com.histmersion.api.*
import com.histmersion.config.AppConfig
import com.histmersion.domain.catalogue.places.{PlaceScope, PlaceService}

import hearth.kindlings.optics.syntax.*

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
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult =
    search.sources(filters.page(f), cfg().limits.snippetWords, filters.scope(f))

  /** How many sources, findings, articles and events `f` selects. */
  def selectionCounts(f: Filter): PlaceCounts = search.selectionCounts(f, filters.scope(f))

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
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val unit = norm(q.unit, "sources")
    Tree
      .parseBy(norm(q.by, Tree.KindTime))
      .toRight(
        ApiError(
          s"unsupported by='${norm(q.by, Tree.KindTime)}' — use " +
            "time | region | time,region | region,time"
        )
      )
      .flatMap { axes =>
        if unit != "sources" && unit != "findings" && unit != Aggregate.UnitStarts then
          Left(ApiError(s"unsupported unit='$unit' — use sources, findings or starts"))
        else aggregateOn(axes, unit, q)
      }

  /** The aggregation itself, once `by`/`unit` have been validated.
    *
    * The window is CLAMPED to the configured deep-time span (`ui.time.min/max`) and the resulting bucket count is
    * capped (`ui.max-buckets`). Without either, `from=-1e15&to=1e15` asked for 400 000 000 000 buckets: the request
    * built them one `TimeBucket` at a time until the heap gave out, and `to = Long.MaxValue` overflowed the bucket
    * arithmetic on top of that.
    */
  private def aggregateOn(axes: List[String], unit: String, q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    val ui = cfg().ui
    val t = ui.time
    // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
    val a = Filter.lowerGiven(q.filter.period).getOrElse(t.defaultFrom)
    val b = Filter.upperGiven(q.filter.period).getOrElse(t.defaultTo)
    val (lo, hi) = if a <= b then (a, b) else (b, a)
    val (from, to) = (clampToSpan(lo, t.min, t.max), clampToSpan(hi, t.min, t.max))
    val size = q.size.filter(_ > 0).getOrElse(Aggregate.bucketSize(to - from))
    val wanted = Aggregate.bucketCount(from, to, size)
    if wanted > BigInt(math.max(1, ui.maxBuckets)) then
      Left(
        ApiError(
          s"that window needs $wanted buckets of $size year(s), over the " +
            s"${ui.maxBuckets} allowed (ui.max-buckets) — narrow `from`/`to`"
        )
      )
    else Right(aggregateWindow(axes, unit, q, from, to, size))

  /** A caller's year, forced inside the configured span. `ui.time.min/max` is what the web UI can draw and what the
    * corpus covers; nothing outside it can have a non-empty answer anyway.
    */
  private def clampToSpan(v: Long, min: Long, max: Long): Long = math.max(min, math.min(max, v))

  private def aggregateWindow(
      axes: List[String],
      unit: String,
      q: Api.AggregateQuery,
      from: Long,
      to: Long,
      size: Long
  ): TimeHistogram =
    val ui = cfg().ui
    val window = q.filter.modify(_.period).setTo(Some(Period(from, to)))
    val scope = filters.scope(window)
    val depth = q.depth.filter(_ > 0).getOrElse(ui.treeDepth).max(1).min(Tree.MaxDepth)
    val samples = q.samples.getOrElse(ui.treeSamples).max(0).min(Tree.MaxSamples)
    // `unit=starts` is the SOURCE unit counted a different way (plan §1.7): same rows, one bar each,
    // by where the period begins. The rows query therefore still asks for sources.
    val starts = unit == Aggregate.UnitStarts
    val rowUnit = if starts then "sources" else unit
    val counting = if starts then Aggregate.Counting.Starts else Aggregate.Counting.Overlap
    val flat =
      if axes.headOption.contains(Tree.KindTime) then
        Aggregate.histogram(search.periods(rowUnit, window, scope), from, to, size, counting)
      else Nil
    val base = TimeHistogram(from, to, size, flat, by = axes.mkString(","), unit = unit, depth = depth)
    // The TIMELINE STRIP asks for `by=time` on every span change and only wants `buckets`; building
    // the tree as well would double its cost for nothing. A caller that wants the nested answer for
    // the same axis says so by asking for a `depth`/`samples` — which is exactly what the "by time"
    // tab does, and what the CLI's `--group time` sends.
    if axes == List(Tree.KindTime) && q.depth.isEmpty && q.samples.isEmpty then base
    else treeOn(axes, rowUnit, window, scope, from, to, depth, samples, q.asOf, base)

  /** The nested part of [[aggregateOn]] — split out so the flat strip never pays for it. */
  private def treeOn(
      axes: List[String],
      unit: String,
      window: Filter,
      scope: PlaceScope,
      from: Long,
      to: Long,
      depth: Int,
      samples: Int,
      askedAsOf: Option[Long],
      base: TimeHistogram
  ): TimeHistogram =
    val rows = search.treeRows(unit, window, scope)
    // the gazetteer slice these rows hang off — one upward closure, not the whole 16 k-place table
    def gaz = places.gazetteer(rows.flatMap(_.placeIds))
    // `by=region` reads the hierarchy at ONE year: the caller's, else the middle of the window
    val asOf = askedAsOf.orElse(Some(Tree.midpoint(from, to)))
    // LAZY DEEPER LEVELS: `place=<one gazetteer id>` roots the region tree AT that place, so
    // expanding a node returns its subtree rather than the whole forest with the node buried in it.
    // (Two place chips, or a name that is not a gazetteer id, is an ordinary filter — no re-rooting.)
    val root = window.places.filter(p => scope.idsFor(p).isDefined) match
      case one :: Nil => Some(one)
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
      .setTo(rows.iterator.map(_.key).toSet.size)
      // `time,region` reads the gazetteer at each bucket's OWN midpoint, so no single asOf applies
      .modify(_.asOf)
      .setTo(if axes == List(Tree.KindTime, Tree.KindRegion) || axes == List(Tree.KindTime) then None else asOf)
      .modify(_.tree)
      .setTo(tree)
