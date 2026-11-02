package com.histmersion.api.library.search

import com.histmersion.api.*
import com.histmersion.api.catalogue.places.RegionNode

object Tree:

  val MaxDepth: Int = 3

  val MaxSamples: Int = 5

  val KindTime = "time"
  val KindRegion = "region"
  val KeyUnlocated = "unlocated"

  final case class TreeRow(
      key: String,
      citekey: String,
      title: String,
      start: Option[Long] = None,
      end: Option[Long] = None,
      placeIds: List[String] = Nil
  ):
    def toPeriodRow: PeriodRow = PeriodRow(start, end)

  def coversAsOf(p: Option[Period], asOf: Option[Long]): Boolean = (p, asOf) match
    case (None, _)                     => true
    case (_, None)                     => true
    case (Some(Period(a, b)), Some(y)) => y >= a && y <= b

  private def overlaps(row: TreeRow, from: Long, to: Long): Boolean =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a <= to && b >= from)

  def midpoint(start: Long, end: Long): Long = start + Math.floorDiv(end - start, 2L)

  def timeTime(rows: List[TreeRow], from: Long, to: Long, depth: Int, samples: Int = 0): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(rows, lo, hi, Aggregate.bucketSize(hi - lo), clamp(depth), samples, (_, _, _) => (Nil, 0))

  def timeTree(rows: List[TreeRow], from: Long, to: Long, depth: Int, samples: Int = 0): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  private def timeLevel(
      rows: List[TreeRow],
      from: Long,
      to: Long,
      size: Long,
      depth: Int,
      samples: Int,
      nest: (List[TreeRow], Long, Long) => (List[Bucket], Int)
  ): List[Bucket] =
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
              case (Nil, _) => (Nil, 0)
              case (ks, o)  => (ks, o)
        Some(
          Bucket(
            label = Markdown.periodLabel(Some(bStart), Some(bEnd)),
            key = s"$KindTime:$bStart:$bEnd",
            kind = KindTime,
            start = Some(bStart),
            end = Some(bEnd),
            count = count,
            overlap = over,
            children = kids,
            samples = if kids.isEmpty then sampleOf(mine, samples) else Nil
          )
        )
    }

  val SplitRatio: Double = 0.8

  def splitWorthShowing(parent: Int, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists(_.count < parent * SplitRatio) then kids else Nil

  def regionTree(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[PlaceRelation],
      asOf: Option[Long],
      depth: Int,
      samples: Int = 0,
      root: Option[String] = None
  ): List[Bucket] =
    regions(rows, nodes, relations, asOf, clamp(depth), samples, (_, _) => Nil, root)._1

  def timeRegionTree(
      rows: List[TreeRow],
      from: Long,
      to: Long,
      nodes: List[RegionNode],
      relations: List[PlaceRelation],
      depth: Int,
      samples: Int = 0
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(
      rows,
      lo,
      hi,
      Aggregate.bucketSize(hi - lo),
      2,
      samples,
      (mine, bStart, bEnd) =>
        regions(mine, nodes, relations, Some(midpoint(bStart, bEnd)), clamp(depth), samples, (_, _) => Nil)
    )

  def regionTimeTree(
      rows: List[TreeRow],
      from: Long,
      to: Long,
      nodes: List[RegionNode],
      relations: List[PlaceRelation],
      asOf: Option[Long],
      depth: Int,
      samples: Int = 0,
      root: Option[String] = None
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    regions(
      rows,
      nodes,
      relations,
      asOf,
      clamp(depth),
      samples,
      (mine, _) => timeTime(mine, lo, hi, 1, samples),
      root
    )._1

  private def regions(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[PlaceRelation],
      asOf: Option[Long],
      depth: Int,
      samples: Int,
      leaf: (List[TreeRow], String) => List[Bucket],
      root: Option[String] = None
  ): (List[Bucket], Int) =
    val byId = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: String) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges = relations.filter(r =>
      r.kind == PlaceRelation.PartOf && coversAsOf(r.period, asOf) && live(r.childId) && live(r.parentId)
    )
    val kidsOf = edges.groupMap(_.parentId)(_.childId).view.mapValues(_.distinct).toMap
    val parents = edges.groupMap(_.childId)(_.parentId).view.mapValues(_.distinct).toMap

    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: String): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    val drawnAt = scala.collection.mutable.Map.empty[String, Unit]

    def level(ids: List[String], seen: Set[String], left: Int): List[Bucket] =
      ids.distinct
        .filterNot(seen.contains)
        .map(id => (id, at(id)))
        .filter((_, mine) => mine.nonEmpty)
        .sortBy((id, mine) => (-distinct(mine), byId.get(id).fold("")(_.label.toLowerCase), id))
        .flatMap { (id, mine) =>
          if drawnAt.contains(id) then None
          else
            drawnAt.update(id, ())
            val node = byId(id)
            val allKids = kidsOf.getOrElse(id, Nil).filterNot(seen.contains)
            val kids =
              if left <= 1 then leaf(mine, id)
              else
                level(allKids, seen + id, left - 1) match
                  case Nil => leaf(mine, id)
                  case ks  => ks
            val elsewhere = parents
              .getOrElse(id, Nil)
              .filterNot(seen.contains)
              .flatMap(byId.get)
              .filter(p => coversAsOf(p.period, asOf))
              .map(_.label)
              .distinct
            Some(
              Bucket(
                label = node.label,
                key = node.id,
                kind = KindRegion,
                start = node.period.map(_.start),
                end = node.period.map(_.end),
                placeId = Some(node.id),
                count = distinct(mine),
                overlap = overlapOf(mine, allKids.map(at)),
                contested = node.contested,
                children = kids,
                alsoUnder = elsewhere,
                samples = if kids.isEmpty then sampleOf(mine, samples) else Nil
              )
            )
        }

    val rootIds = root.filter(id => byId.get(id).exists(n => coversAsOf(n.period, asOf))) match
      case Some(id) => List(id)
      case None     =>
        byId.values.iterator
          .filter(n => coversAsOf(n.period, asOf) && parents.getOrElse(n.id, Nil).isEmpty)
          .filter(RegionNode.rootEligible)
          .map(_.id)
          .toList
    val located = level(rootIds, Set.empty, depth)
    val shown = rendered(located)
    val lost = rows.filter(r => reach(r.key).intersect(shown).isEmpty)
    val roots =
      if lost.isEmpty then located
      else
        located :+ Bucket(
          label = KeyUnlocated,
          key = KeyUnlocated,
          kind = KindRegion,
          count = distinct(lost),
          samples = sampleOf(lost, samples)
        )
    (roots, overlapOf(rows, rootIds.map(at)))

  private def rendered(buckets: List[Bucket]): Set[String] =
    buckets.iterator.flatMap(b => b.placeId.toSet ++ rendered(b.children)).toSet

  private def ancestors(start: Set[String], parents: Map[String, List[String]]): Set[String] =
    @annotation.tailrec
    def go(frontier: Set[String], seen: Set[String]): Set[String] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  private def distinct(rows: List[TreeRow]): Int = rows.iterator.map(_.key).toSet.size

  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): Int =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then 0
    else rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1)

  private def sampleOf(rows: List[TreeRow], n: Int): List[BucketSample] =
    if n <= 0 then Nil
    else rows.iterator.map(r => BucketSample(r.citekey, r.title)).distinctBy(_.citekey).take(n).toList

  private def clamp(depth: Int): Int = depth.max(1).min(MaxDepth)
  private def order(a: Long, b: Long): (Long, Long) = if a <= b then (a, b) else (b, a)

  def parseBy(s: String): Option[List[String]] =
    val parts = s.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some(parts)
    else None
