package com.histmersion.api.library
import com.histmersion.api.Markdown
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

object Tree:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxDepthCount = MaxDepthCount.T
  object MaxDepthCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxSamplesCount = MaxSamplesCount.T
  object MaxSamplesCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NodeId = NodeId.T
  object NodeId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Span = Span.T
  object Span extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitRatioAmount = SplitRatioAmount.T
  object SplitRatioAmount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type MaxDepth = MaxDepthValue
  type MaxSamples = MaxSamplesValue
  type TimeTimeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeTimeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeTimeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeTimeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type TimeTreeFrom = _root_.com.histmersion.api.catalogue.Year
  val TimeTreeFrom: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type TimeTreeTo = _root_.com.histmersion.api.catalogue.Year
  val TimeTreeTo: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type TimeTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type TimeLevelSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeLevelSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type SplitRatio = SplitRatioValue
  type RegionTreeAsOf = _root_.com.histmersion.api.catalogue.Year
  val RegionTreeAsOf: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type RegionTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val RegionTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type RegionTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val RegionTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type RegionTreeRoot = _root_.com.histmersion.api.library.Tree.NodeId
  val RegionTreeRoot: _root_.com.histmersion.api.library.Tree.NodeId.type = _root_.com.histmersion.api.library.Tree.NodeId
  type TimeRegionTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeRegionTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeRegionTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeRegionTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type RegionTimeTreeAsOf = _root_.com.histmersion.api.catalogue.Year
  val RegionTimeTreeAsOf: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year
  type RegionTimeTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val RegionTimeTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type RegionTimeTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val RegionTimeTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type RegionTimeTreeRoot = _root_.com.histmersion.api.library.Tree.NodeId
  val RegionTimeTreeRoot: _root_.com.histmersion.api.library.Tree.NodeId.type = _root_.com.histmersion.api.library.Tree.NodeId
  type RegionsAsOf = _root_.com.histmersion.api.catalogue.Year
  val RegionsAsOf: _root_.com.histmersion.api.catalogue.Year.type = _root_.com.histmersion.api.catalogue.Year

  val MaxDepth: com.histmersion.api.library.Tree.MaxDepth = _root_.com.histmersion.api.library.Tree.MaxDepthCount(3)

  val MaxSamples: com.histmersion.api.library.Tree.MaxSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount(5)

  val KindTime = "time"
  val KindRegion = "region"
  val KeyUnlocated = "unlocated"

  final case class TreeRow(
      key: TreeRow.Key,
      citekey: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      start: Option[TreeRow.Start] = None,
      end: Option[TreeRow.End] = None,
      placeIds: List[TreeRow.PlaceIds] = Nil
  ):
    def toPeriodRow: Period.Row = Period.Row(start, end)

  object TreeRow:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceIds = PlaceIds.T
    object PlaceIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def coversAsOf(p: Option[Period], asOf: Option[com.histmersion.api.catalogue.Year]): Boolean = (p, asOf) match
    case (None, _)                     => (true)
    case (_, None)                     => (true)
    case (Some(Period(a, b)), Some(y)) => y >= a && y <= b

  private def overlaps(row: TreeRow, from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year): Boolean =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a <= to && b >= from)

  def midpoint(start: com.histmersion.api.catalogue.Year, end: com.histmersion.api.catalogue.Year): com.histmersion.api.catalogue.Year = _root_.com.histmersion.api.catalogue.Year((start).value + Math.floorDiv((end).value - (start).value, 2L))

  def timeTime(rows: List[TreeRow], from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year, depth: com.histmersion.api.library.Tree.TimeTimeDepth, samples: com.histmersion.api.library.Tree.TimeTimeSamples = _root_.com.histmersion.api.library.Tree.TimeTimeSamples(0)): List[Bucket] =
    val (lo, hi) = order(_root_.com.histmersion.api.catalogue.Year((from).value), _root_.com.histmersion.api.catalogue.Year((to).value))
    timeLevel(rows, _root_.com.histmersion.api.catalogue.Year((lo).value), _root_.com.histmersion.api.catalogue.Year((hi).value), Aggregate.bucketSize((hi).value - lo), _root_.com.histmersion.api.library.Tree.Depth((clamp(depth)).value), samples, (_, _, _) => (Nil, _root_.com.histmersion.api.library.Tree.Count(0)))

  def timeTree(rows: List[TreeRow], from: com.histmersion.api.library.Tree.TimeTreeFrom, to: com.histmersion.api.library.Tree.TimeTreeTo, depth: com.histmersion.api.library.Tree.TimeTreeDepth, samples: com.histmersion.api.library.Tree.TimeTreeSamples = _root_.com.histmersion.api.library.Tree.TimeTreeSamples(0)): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  private def timeLevel(
      rows: List[TreeRow],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      size: com.histmersion.api.library.Tree.Span,
      depth: com.histmersion.api.library.Tree.Depth,
      samples: com.histmersion.api.library.Tree.TimeLevelSamples,
      nest: (List[TreeRow], com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year) => (List[Bucket], com.histmersion.api.library.Tree.Count)
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

  val SplitRatio: com.histmersion.api.library.Tree.SplitRatio = _root_.com.histmersion.api.library.Tree.SplitRatioAmount(0.8)

  def splitWorthShowing(parent: com.histmersion.api.library.Tree.Count, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists((_.count).value < (parent).value * (SplitRatio).value) then kids else Nil

  def regionTree(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionTreeAsOf],
      depth: com.histmersion.api.library.Tree.RegionTreeDepth,
      samples: com.histmersion.api.library.Tree.RegionTreeSamples = _root_.com.histmersion.api.library.Tree.RegionTreeSamples(0),
      root: Option[com.histmersion.api.library.Tree.RegionTreeRoot] = None
  ): List[Bucket] =
    regions(rows, nodes, relations, asOf, _root_.com.histmersion.api.library.Tree.MaxDepthCount((clamp(depth)).value), samples, (_, _) => Nil, root)._1

  def timeRegionTree(
      rows: List[TreeRow],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      depth: com.histmersion.api.library.Tree.TimeRegionTreeDepth,
      samples: com.histmersion.api.library.Tree.TimeRegionTreeSamples = _root_.com.histmersion.api.library.Tree.TimeRegionTreeSamples(0)
  ): List[Bucket] =
    val (lo, hi) = order(_root_.com.histmersion.api.catalogue.Year((from).value), _root_.com.histmersion.api.catalogue.Year((to).value))
    timeLevel(
      rows,
      _root_.com.histmersion.api.catalogue.Year((lo).value),
      _root_.com.histmersion.api.catalogue.Year((hi).value),
      Aggregate.bucketSize((hi).value - lo),
      _root_.com.histmersion.api.library.Tree.Depth(2),
      samples,
      (mine, bStart, bEnd) =>
        { val refactorTuple = regions(mine, nodes, relations, Some(_root_.com.histmersion.api.catalogue.Year((midpoint(_root_.com.histmersion.api.catalogue.Year((bStart).value), _root_.com.histmersion.api.catalogue.Year((bEnd).value))).value)), _root_.com.histmersion.api.library.Tree.MaxDepthCount((clamp(depth)).value), samples, (_, _) => Nil); (refactorTuple._1, _root_.com.histmersion.api.library.Tree.Count((refactorTuple._2).value)) }
    )

  def regionTimeTree(
      rows: List[TreeRow],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionTimeTreeAsOf],
      depth: com.histmersion.api.library.Tree.RegionTimeTreeDepth,
      samples: com.histmersion.api.library.Tree.RegionTimeTreeSamples = _root_.com.histmersion.api.library.Tree.RegionTimeTreeSamples(0),
      root: Option[com.histmersion.api.library.Tree.RegionTimeTreeRoot] = None
  ): List[Bucket] =
    val (lo, hi) = order(_root_.com.histmersion.api.catalogue.Year((from).value), _root_.com.histmersion.api.catalogue.Year((to).value))
    regions(
      rows,
      nodes,
      relations,
      asOf,
      _root_.com.histmersion.api.library.Tree.MaxDepthCount((clamp(depth)).value),
      samples,
      (mine, _) => timeTime(mine, _root_.com.histmersion.api.catalogue.Year((lo).value), _root_.com.histmersion.api.catalogue.Year((hi).value), _root_.com.histmersion.api.library.Tree.Depth(1), samples),
      root
    )._1

  private def regions(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionsAsOf],
      depth: com.histmersion.api.library.Tree.MaxDepthCount,
      samples: com.histmersion.api.library.Tree.MaxSamplesCount,
      leaf: (List[TreeRow], com.histmersion.api.library.Tree.NodeId) => List[Bucket],
      root: Option[com.histmersion.api.library.Tree.NodeId] = None
  ): (List[Bucket], com.histmersion.api.library.Tree.Count) =
    val byId = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: com.histmersion.api.library.Tree.NodeId) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges = relations.filter(r =>
      r.kind == _root_.com.histmersion.api.catalogue.Place.Relation.Kind(Place.Relation.PartOf) && ((coversAsOf(r.period, asOf)) == (true)) && live(r.childId) && live(r.parentId)
    )
    val kidsOf = edges.groupMap(_.parentId)(_.childId).view.mapValues(_.distinct).toMap
    val parents = edges.groupMap(_.childId)(_.parentId).view.mapValues(_.distinct).toMap

    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: com.histmersion.api.library.Tree.NodeId): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    val drawnAt = scala.collection.mutable.Map.empty[String, Unit]

    def level(ids: List[com.histmersion.api.library.Tree.NodeId], seen: Set[com.histmersion.api.library.Tree.NodeId], left: Int): List[Bucket] =
      ids.distinct
        .filterNot(seen.contains)
        .map(id => (id, at(id)))
        .filter((_, mine) => mine.nonEmpty)
        .sortBy((id, mine) => (-(distinct(mine)).value, byId.get(id).fold("")(_.label.toLowerCase), id))
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
          .filter(n => ((coversAsOf(n.period, asOf)) == (true)) && parents.getOrElse(n.id, Nil).isEmpty)
          .filter(((RegionNode.rootEligible) == (true)))
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

  private def ancestors(start: Set[com.histmersion.api.library.Tree.NodeId], parents: Map[com.histmersion.api.library.Tree.NodeId, List[com.histmersion.api.library.Tree.NodeId]]): Set[com.histmersion.api.library.Tree.NodeId] =
    @annotation.tailrec
    def go(frontier: Set[com.histmersion.api.library.Tree.NodeId], seen: Set[com.histmersion.api.library.Tree.NodeId]): Set[com.histmersion.api.library.Tree.NodeId] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  private def distinct(rows: List[TreeRow]): Int = (rows.iterator.map(_.key).toSet.size)

  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): Int =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then (0)
    else (rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1))

  private def sampleOf(rows: List[TreeRow], n: com.histmersion.api.library.Tree.MaxSamplesCount): List[Bucket.Sample] =
    if n <= 0 then Nil
    else rows.iterator.map(r => Bucket.Sample(r.citekey, r.title)).distinctBy(_.citekey).take((n).value).toList

  private def clamp(depth: com.histmersion.api.library.Tree.Depth): com.histmersion.api.library.Tree.Depth = _root_.com.histmersion.api.library.Tree.Depth((depth).value.max(1).min((MaxDepth).value))
  private def order(a: com.histmersion.api.catalogue.Year, b: com.histmersion.api.catalogue.Year): (com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year) = if a <= b then (_root_.com.histmersion.api.catalogue.Year((a).value), _root_.com.histmersion.api.catalogue.Year((b).value)) else (_root_.com.histmersion.api.catalogue.Year((b).value), _root_.com.histmersion.api.catalogue.Year((a).value))

  def parseBy(s: String): Option[List[String]] =
    val parts = (s).value.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some((parts).map(refactorValue11 => (refactorValue11)))
    else None
