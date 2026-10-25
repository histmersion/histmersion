package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.PlaceKind

/** NESTED GROUPING by deep time and by the gazetteer (plan §1.6 / §1.6a) — **pure**, no database.
  *
  * `TreeRepo` selects the rows (one per counted unit: a source, or a finding for `unit=findings`)
  * together with the period and the gazetteer places they were materialised into; everything below
  * turns those rows into the [[Bucket]] tree that `/api/aggregate?by=…` returns, the CLI prints and
  * the web UI draws. Bucketing therefore lives in ONE place and is unit-testable without Postgres —
  * the same reasoning as [[Aggregate]], whose adaptive sizes this reuses verbatim.
  *
  * Two rules run through all of it:
  *
  *  - **Counts are non-additive.** A unit is counted in EVERY bucket it overlaps (time) and in every
  *    place that covers it (region), so children never sum to their parent. [[Bucket.overlap]] says
  *    how many of a node's units sit in more than one of its children, which is what lets the UI
  *    print "12 sources, 3 in more than one subregion" instead of pretending the numbers add up.
  *  - **The hierarchy is read AS OF a year.** A `part_of` edge and a place both carry their own
  *    validity; only those covering `asOf` are walked. That is why the same Hattusa finding sits
  *    under the Hittite Empire at −1400 and under Phrygia at −700, and why `by=time,region` reads
  *    the gazetteer at each time bucket's own midpoint.
  */
object Tree:

  /** The deepest nesting the API will build (plan §1.6: "up to depth 3"). */
  val MaxDepth: Int = 3

  /** The most sample sources a leaf may carry (plan §1.6: "0–5 sample sources per leaf"). A cap on
    * the SHAPE of the contract, not a tunable — the default count is config (`ui.tree-samples`). */
  val MaxSamples: Int = 5

  /** `time` buckets carry `start`/`end`; `region` buckets carry `placeId` (empty for `unlocated`). */
  val KindTime      = "time"
  val KindRegion    = "region"
  val KeyUnlocated  = "unlocated"

  /** One counted unit as the repo selected it: a SOURCE (`key` = citekey) or, for `unit=findings`,
    * one finding (`key` = finding id, `citekey`/`title` still name the source it belongs to).
    * `placeIds` are the gazetteer places the unit was materialised into (`finding_places`); empty =
    * unlocated. Undated (`start`/`end` both `None`) units are dropped by the time levels, exactly as
    * in [[Aggregate]]. */
  final case class TreeRow(
      key: String,
      citekey: String,
      title: String,
      start: Option[Long] = None,
      end: Option[Long] = None,
      placeIds: List[String] = Nil
  ):
    def toPeriodRow: PeriodRow = PeriodRow(start, end)

  /** A gazetteer node as the tree needs it: what to label it, and when it was itself valid. (The
    * full row is [[PlaceSummary]]; the tree only ever needs these four fields.) */
  final case class RegionNode(
      id: String,
      label: String,
      kind: String,
      period: Option[Period] = None,
      contested: Boolean = false,
      /** Pleiades marks superseded records `withdrawn`; they must never head a level. */
      withdrawn: Boolean = false
  )

  // ── which nodes may HEAD the region tree ──────────────────────────────────────────────────────

  /** Kinds that can be a top level. A `site` or a `feature` is a leaf by construction — Pleiades
    * contributes ~15 700 of them — and a parentless one ("Asopos (river)", "Euboia (mountain)")
    * heading the forest buries the continents under a list of rivers. */
  val RootKinds: Set[String] = Set(PlaceKind.Region, PlaceKind.Polity, "continent")

  /** Pleiades spells an uncertain identification into the LABEL: a trailing `?` on the name or a
    * part of it ("Theren? (river)", "Kisamon? Pr."), and a leading `*` for a reconstructed name
    * ("*Granouas (river)"). Those are hypotheses about what a place was called, not levels of a
    * geography, and they have no business being one of the handful of rows a reader starts from. */
  def uncertainLabel(label: String): Boolean =
    val l = label.trim
    l.startsWith("*") || l.contains("?") || l.toLowerCase.startsWith("withdrawn")

  /** May this node be a ROOT of the region tree? Nothing here removes a node from the tree — a
    * river still appears under the region that contains it — it only decides what the top level is
    * allowed to be. */
  def rootEligible(n: RegionNode): Boolean =
    RootKinds.contains(n.kind) && !n.withdrawn && !uncertainLabel(n.label)

  // ── validity as of a year ─────────────────────────────────────────────────────────────────────

  /** A period covers `asOf`; a missing period means "always", and a missing `asOf` means "whenever". */
  def coversAsOf(p: Option[Period], asOf: Option[Long]): Boolean = (p, asOf) match
    case (None, _)                => true
    case (_, None)                => true
    case (Some(Period(a, b)), Some(y)) => y >= a && y <= b

  /** Inclusive `[start, end]` overlap — the Scala twin of `int8range(a, b, '[]') &&`. */
  private def overlaps(row: TreeRow, from: Long, to: Long): Boolean =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a <= to && b >= from)

  /** The middle of an inclusive bucket, rounded DOWN so the same bucket always reads the gazetteer
    * at the same year (`by=time,region` nests the hierarchy as of this). */
  def midpoint(start: Long, end: Long): Long = start + Math.floorDiv(end - start, 2L)

  // ── the time axis ─────────────────────────────────────────────────────────────────────────────

  /** Time buckets over `[from, to]` at the adaptive size for that span, each nesting its children at
    * [[Aggregate.finer]] down to `depth` levels (clamped to [[MaxDepth]]).
    *
    * A unit is counted in every bucket its period overlaps, so counts do not sum; undated units are
    * absent. Empty buckets are dropped — a tree row reading "0" is noise (the timeline strip, which
    * DOES need the gaps, keeps using [[Aggregate.histogram]]). Leaves carry up to `samples` sample
    * sources, in the order the repo returned them (i.e. under the caller's `sort=`). */
  def timeTime(rows: List[TreeRow], from: Long, to: Long, depth: Int, samples: Int = 0): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(rows, lo, hi, Aggregate.bucketSize(hi - lo), clamp(depth), samples, (_, _, _) => (Nil, 0))

  /** [[timeTime]] under its plan name (`by=time`). */
  def timeTree(rows: List[TreeRow], from: Long, to: Long, depth: Int, samples: Int = 0): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  /** One level of time buckets. `nest` produces the children (and their overlap count) of a bucket
    * when something other than a finer time level belongs there — that is how `time,region` hangs
    * the gazetteer, read at the bucket's own midpoint, under each bucket. */
  private def timeLevel(rows: List[TreeRow], from: Long, to: Long, size: Long, depth: Int,
      samples: Int, nest: (List[TreeRow], Long, Long) => (List[Bucket], Int)): List[Bucket] =
    val finer = Aggregate.finer(size)
    Aggregate.buckets(from, to, size).flatMap { (bStart, bEnd) =>
      val mine = rows.filter(overlaps(_, bStart, bEnd))
      if mine.isEmpty then Nil
      else
        val count = distinct(mine)
        val (kids, over) =
          if depth <= 1 then (Nil, 0)
          else
            nest(mine, bStart, bEnd) match
              case (Nil, _) if finer < size =>
                (splitWorthShowing(count, timeLevel(mine, bStart, bEnd, finer, depth - 1, samples, nest)), 0)
              case (Nil, _)  => (Nil, 0)
              // finer TIME children are not an overlap worth reporting (a period spanning two
              // buckets is the normal case); REGION children are — that is the non-additive fact.
              case (ks, o)   => (ks, o)
        Some(Bucket(
          label = Markdown.periodLabel(Some(bStart), Some(bEnd)),
          key = s"$KindTime:$bStart:$bEnd",
          kind = KindTime,
          start = Some(bStart),
          end = Some(bEnd),
          count = count,
          overlap = over,
          children = kids,
          samples = if kids.isEmpty then sampleOf(mine, samples) else Nil))
    }

  /** How much smaller than its parent a time child has to be for the finer level to be worth
    * drawing at all. */
  val SplitRatio: Double = 0.8

  /** Finer TIME buckets only earn their rows when they SAY something.
    *
    * A unit sits in every bucket its period overlaps, and this corpus is mostly long-range
    * syntheses: a −3300…−1200 source is in all 21 hundred-year children of its millennium, so every
    * child carries very nearly the parent's own count. Expanded, that was 341 rows of near-identical
    * numbers with nothing in them to choose between — the reader scrolls a wall of "34 sources".
    *
    * So the level is kept only when at least one child is materially smaller than the parent (below
    * [[SplitRatio]] of it), i.e. when the split actually separates the evidence. Otherwise the
    * parent is a leaf and shows its sample sources instead, which is the thing worth looking at. */
  def splitWorthShowing(parent: Int, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists(_.count < parent * SplitRatio) then kids else Nil

  // ── the region axis ───────────────────────────────────────────────────────────────────────────

  /** The gazetteer levels from the roots (continents) down, `depth` levels deep, AS OF `asOf`.
    *
    * A unit belongs to a node when the node is one of its places or an ANCESTOR of one, walking only
    * `part_of` edges whose own period — and whose endpoints' own periods — cover `asOf`. Counts are
    * therefore non-additive and [[Bucket.overlap]] reports how many units are in more than one child.
    * Units whose places are all invalid at `asOf` (or which have none) land in the trailing
    * `unlocated` bucket. Nodes no unit reaches are dropped. */
  def regionTree(rows: List[TreeRow], nodes: List[RegionNode], relations: List[PlaceRelation],
      asOf: Option[Long], depth: Int, samples: Int = 0, root: Option[String] = None): List[Bucket] =
    regions(rows, nodes, relations, asOf, clamp(depth), samples, (_, _) => Nil, root)._1

  /** `by=time,region`: time buckets first, then INSIDE each bucket the gazetteer as it stood at that
    * bucket's own midpoint — so the same finding sits under the Hittite Empire in −1600…−1200 and
    * under Phrygia in −800…−600. `depth` counts the REGION levels below the (single) time level. */
  def timeRegionTree(rows: List[TreeRow], from: Long, to: Long, nodes: List[RegionNode],
      relations: List[PlaceRelation], depth: Int, samples: Int = 0): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(rows, lo, hi, Aggregate.bucketSize(hi - lo), 2, samples,
      (mine, bStart, bEnd) =>
        regions(mine, nodes, relations, Some(midpoint(bStart, bEnd)), clamp(depth), samples,
          (_, _) => Nil))

  /** `by=region,time`: the gazetteer first (as of `asOf`), then a single level of time buckets under
    * each region LEAF — "where, and then when inside there". */
  def regionTimeTree(rows: List[TreeRow], from: Long, to: Long, nodes: List[RegionNode],
      relations: List[PlaceRelation], asOf: Option[Long], depth: Int, samples: Int = 0,
      root: Option[String] = None): List[Bucket] =
    val (lo, hi) = order(from, to)
    regions(rows, nodes, relations, asOf, clamp(depth), samples,
      (mine, _) => timeTime(mine, lo, hi, 1, samples), root)._1

  /** Shared machinery of every region level; returns the roots and how many units reach more than
    * one of them (which is the `overlap` a `time,region` bucket reports). `leaf` supplies the
    * children of a node the region recursion stopped at — `region,time` hangs time buckets there,
    * plain `region` hangs nothing. */
  private def regions(rows: List[TreeRow], nodes: List[RegionNode], relations: List[PlaceRelation],
      asOf: Option[Long], depth: Int, samples: Int,
      leaf: (List[TreeRow], String) => List[Bucket],
      root: Option[String] = None): (List[Bucket], Int) =
    val byId    = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: String) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges   = relations.filter(r =>
      r.kind == PlaceRelation.PartOf && coversAsOf(r.period, asOf) && live(r.childId) && live(r.parentId))
    val kidsOf  = edges.groupMap(_.parentId)(_.childId).view.mapValues(_.distinct).toMap
    val parents = edges.groupMap(_.childId)(_.parentId).view.mapValues(_.distinct).toMap

    // which nodes each unit reaches: its own places that are valid at `asOf`, plus their ancestors
    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: String): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    // ONE PATH PER PLACE. The gazetteer is a DAG, not a tree: "Anatolia" is part of both Asia and
    // Europe, so a naive walk draws it (and its whole subtree, and its counts) twice and the reader
    // has no way to tell the two copies apart. It is drawn under the first parent that reaches it —
    // the biggest, since siblings are ordered by count — and that copy says where else it sits.
    val drawnAt = scala.collection.mutable.Map.empty[String, Unit]

    def level(ids: List[String], seen: Set[String], left: Int): List[Bucket] =
      // Ordered BEFORE the walk, not after: siblings are ranked by count, and "drawn under the
      // biggest parent that reaches it" is only true if the biggest one gets there first.
      ids.distinct.filterNot(seen.contains)
        .map(id => (id, at(id)))
        .filter((_, mine) => mine.nonEmpty)
        .sortBy((id, mine) => (-distinct(mine), byId.get(id).fold("")(_.label.toLowerCase), id))
        .flatMap { (id, mine) =>
        if drawnAt.contains(id) then None
        else
          drawnAt.update(id, ())
          val node    = byId(id)
          val allKids = kidsOf.getOrElse(id, Nil).filterNot(seen.contains)
          val kids =
            if left <= 1 then leaf(mine, id)
            else level(allKids, seen + id, left - 1) match
              case Nil => leaf(mine, id)
              case ks  => ks
          // every OTHER valid parent this node has, named — "(also under Europe)"
          val elsewhere = parents.getOrElse(id, Nil).filterNot(seen.contains)
            .flatMap(byId.get).filter(p => coversAsOf(p.period, asOf)).map(_.label).distinct
          Some(Bucket(
            label = node.label,
            key = node.id,
            kind = KindRegion,
            start = node.period.map(_.start),
            end = node.period.map(_.end),
            placeId = Some(node.id),
            count = distinct(mine),
            // over ALL valid children, not only the rendered ones: truncating at `depth` must not
            // change what "in more than one subregion" means
            overlap = overlapOf(mine, allKids.map(at)),
            contested = node.contested,
            children = kids,
            alsoUnder = elsewhere,
            samples = if kids.isEmpty then sampleOf(mine, samples) else Nil))
      }

    // An explicit `root` is how a client fetches ONE node's subtree lazily: the answer is that node
    // and what is under it, not the whole forest with the node buried three levels down.
    //
    // Otherwise roots are the nodes with no valid parent AND eligible to be one ([[rootEligible]]):
    // a region, polity or continent whose record is neither withdrawn nor an uncertain
    // identification. Everything else still appears in the tree, under whatever contains it — it
    // just does not get to be one of the handful of rows the reader starts from. Without this the
    // seeded corpus opens on 35 roots, 25 of them Pleiades rivers, mountains and
    // "Withdrawn (duplicate): …" records, with the five continents scattered among them.
    val rootIds = root.filter(id => byId.get(id).exists(n => coversAsOf(n.period, asOf))) match
      case Some(id) => List(id)
      case None =>
        byId.values.iterator
          .filter(n => coversAsOf(n.period, asOf) && parents.getOrElse(n.id, Nil).isEmpty)
          .filter(rootEligible)
          .map(_.id).toList
    val located = level(rootIds, Set.empty, depth)
    // "unlocated" means "not counted anywhere ABOVE", not merely "has no place": a unit whose every
    // place is invalid as of `asOf` — or is unreachable from any root, which is what a cyclic
    // gazetteer produces — must still appear somewhere, or the tree quietly loses sources.
    val shown = rendered(located)
    val lost  = rows.filter(r => reach(r.key).intersect(shown).isEmpty)
    val roots =
      if lost.isEmpty then located
      else located :+ Bucket(label = KeyUnlocated, key = KeyUnlocated, kind = KindRegion,
        count = distinct(lost), samples = sampleOf(lost, samples))
    (roots, overlapOf(rows, rootIds.map(at)))

  /** Every gazetteer id that actually made it into the rendered forest. */
  private def rendered(buckets: List[Bucket]): Set[String] =
    buckets.iterator.flatMap(b => b.placeId.toSet ++ rendered(b.children)).toSet

  /** Everything reachable UPWARD from `start` through `parents`, including `start` itself. Bounded by
    * the visited set, so a cyclic gazetteer is harmless rather than a stack overflow. */
  private def ancestors(start: Set[String], parents: Map[String, List[String]]): Set[String] =
    @annotation.tailrec
    def go(frontier: Set[String], seen: Set[String]): Set[String] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  // ── counting ──────────────────────────────────────────────────────────────────────────────────

  private def distinct(rows: List[TreeRow]): Int = rows.iterator.map(_.key).toSet.size

  /** How many of `rows` are counted in more than one of the given child groups — the non-additive
    * fact the UI prints as "N in more than one subregion". */
  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): Int =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then 0
    else rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1)

  private def sampleOf(rows: List[TreeRow], n: Int): List[BucketSample] =
    if n <= 0 then Nil
    else rows.iterator.map(r => BucketSample(r.citekey, r.title)).distinctBy(_.citekey).take(n).toList

  private def clamp(depth: Int): Int = depth.max(1).min(MaxDepth)
  private def order(a: Long, b: Long): (Long, Long) = if a <= b then (a, b) else (b, a)

  /** The axes `/api/aggregate?by=` understands, parsed once here so REST, MCP and the CLI cannot
    * disagree about what `time,region` means. */
  def parseBy(s: String): Option[List[String]] =
    val parts = s.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some(parts) else None

// ── wire DTOs ───────────────────────────────────────────────────────────────────────────────────
// Declared LAST in the file (and `Bucket`, which references `BucketSample`, last of all): the
// native-image metadata indexes the derived lazies per compilation unit.

/** One sample source under a leaf bucket — enough to decide whether to open it, no more. */
final case class BucketSample(citekey: String, title: String) derives KindlingsCodecAsObject
object BucketSample:
  given Schema[BucketSample] = KindlingsSchema.derived[BucketSample].schema

/** One node of the aggregation tree (plan §1.6).
  *
  *  - `kind = "time"` → `start`/`end` are the bucket's inclusive signed years, `placeId` is empty.
  *  - `kind = "region"` → `placeId` is the gazetteer id and `start`/`end` are the PLACE's own
  *    validity (empty for a place valid always, and for the trailing `unlocated` bucket, whose
  *    `key` is `"unlocated"` and which has no `placeId`).
  *
  * `count` is distinct units (sources, or findings for `unit=findings`) in the whole subtree — NOT
  * the sum of the children, because a unit belongs to every bucket it overlaps. `overlap` is how
  * many of them are in more than one child ("12 sources, 3 in more than one subregion"); it is 0
  * where the question is meaningless (finer time buckets). `key` is unique among siblings and is what
  * the UI sends back to fetch the subtree lazily. */
final case class Bucket(
    label: String,
    key: String,
    kind: String,
    start: Option[Long] = None,
    end: Option[Long] = None,
    placeId: Option[String] = None,
    count: Int = 0,
    overlap: Int = 0,
    contested: Boolean = false,
    /** Other parents this node also sits under, by label. The gazetteer is a DAG — "Anatolia" is
      * part of Asia AND of Europe — and a node is drawn under ONE of them, so this is how the other
      * paths stay visible ("(also under Europe)") instead of the subtree being silently duplicated. */
    alsoUnder: List[String] = Nil,
    children: List[Bucket] = Nil,
    samples: List[BucketSample] = Nil
) derives KindlingsCodecAsObject, KindlingsSchema
object Bucket:
  /** [[Bucket]] is RECURSIVE (`children: List[Bucket]`), and that is why its schema is exposed as a
    * `KindlingsSchema[Bucket]` (through the `derives` clause) rather than as the `given Schema[…]`
    * every other DTO in this codebase carries.
    *
    * Kindlings derives recursive types by itself — it emits a tapir `SRef` for the back-edge — but
    * only as long as its "use the implicit already in scope" rule does not fire first. A Scala 3
    * `given` with an ascribed type is visible inside its own right-hand side, so a
    * `given Schema[Bucket] = …derived…` here is found by the derivation when it reaches the
    * `children` field, and the emitted code refers to the very lazy val being initialised: the
    * result compiles and then HANGS on first use (a lazy val awaiting its own latch — tapir's own
    * `Schema.derived` has the same failure, softwaremill/tapir#2749).
    *
    * With only the `KindlingsSchema[Bucket]` instance in scope there is no `Schema[Bucket]` for that
    * rule to find, the self-reference rule fires as intended, and every enclosing DTO
    * ([[TimeHistogram]]) picks this up through the same Kindlings derivation. The schema is still
    * DERIVED from this case class, so the JSON on the wire and the OpenAPI/MCP schema cannot drift.
    *
    * Ask for it with `Bucket.schema` (or `summon[KindlingsSchema[Bucket]].schema`); do NOT add a
    * `given Schema[Bucket]` here. */
  def schema: Schema[Bucket] = summon[KindlingsSchema[Bucket]].schema
