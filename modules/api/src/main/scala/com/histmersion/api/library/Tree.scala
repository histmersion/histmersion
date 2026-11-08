package com.histmersion.api.library
import com.histmersion.api.YearFmt
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

object Tree:
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxSamplesCount = MaxSamplesCount.T
  object MaxSamplesCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitRatioAmount = SplitRatioAmount.T
  object SplitRatioAmount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type MaxDepth = MaxDepthValue
  type MaxDepthValue = com.histmersion.api.library.Tree.Depth
  val MaxDepthValue: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type MaxSamples = MaxSamplesValue
  type MaxSamplesValue = com.histmersion.api.library.Tree.MaxSamplesCount
  val MaxSamplesValue: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type TimeTimeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeTimeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeTimeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeTimeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  export com.histmersion.api.catalogue.{Year as TimeTreeFrom, Year as TimeTreeTo}
  type TimeTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type TimeLevelSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeLevelSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type SplitRatio = SplitRatioValue
  type SplitRatioValue = com.histmersion.api.library.Tree.SplitRatioAmount
  val SplitRatioValue: _root_.com.histmersion.api.library.Tree.SplitRatioAmount.type = _root_.com.histmersion.api.library.Tree.SplitRatioAmount
  export com.histmersion.api.catalogue.{Year as RegionTreeAsOf}
  type RegionTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val RegionTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type RegionTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val RegionTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  export com.histmersion.api.catalogue.RegionNode.{Id as RegionTreeRoot}
  type TimeRegionTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val TimeRegionTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type TimeRegionTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val TimeRegionTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  export com.histmersion.api.catalogue.{Year as RegionTimeTreeAsOf}
  type RegionTimeTreeDepth = _root_.com.histmersion.api.library.Tree.Depth
  val RegionTimeTreeDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type RegionTimeTreeSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  val RegionTimeTreeSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  export com.histmersion.api.catalogue.RegionNode.{Id as RegionTimeTreeRoot}
  export com.histmersion.api.catalogue.{Year as RegionsAsOf}
  type RegionsDepth = com.histmersion.api.library.Tree.Depth
  val RegionsDepth: _root_.com.histmersion.api.library.Tree.Depth.type = _root_.com.histmersion.api.library.Tree.Depth
  type RegionsSamples = com.histmersion.api.library.Tree.MaxSamplesCount
  val RegionsSamples: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount
  type SampleOfN = com.histmersion.api.library.Tree.MaxSamplesCount
  val SampleOfN: _root_.com.histmersion.api.library.Tree.MaxSamplesCount.type = _root_.com.histmersion.api.library.Tree.MaxSamplesCount

  val MaxDepth: com.histmersion.api.library.Tree.MaxDepth = _root_.com.histmersion.api.library.Tree.Depth(3)

  val MaxSamples: com.histmersion.api.library.Tree.MaxSamples = _root_.com.histmersion.api.library.Tree.MaxSamplesCount(5)

  val KindTime = "time"
  val KindRegion = "region"
  val KeyUnlocated = "unlocated"

  final case class TreeRow(
      key: TreeRow.Key,
      citekey: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      start: Option[com.histmersion.api.catalogue.Year] = None,
      end: Option[com.histmersion.api.catalogue.Year] = None,
      placeIds: List[RegionNode.Id] = Nil
  ):
    def toPeriodRow: Period.Row = Period.Row(start, end)

  object TreeRow:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def coversAsOf(p: Option[Period], asOf: Option[com.histmersion.api.catalogue.Year]): Boolean = (p, asOf) match
    case (None, _)                     => (true)
    case (_, None)                     => (true)
    case (Some(Period(a, b)), Some(y)) => y.value >= a.value && y.value <= b.value

  private def overlaps(row: TreeRow, from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year): Boolean =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a.value <= to.value && b.value >= from.value)

  def midpoint(start: com.histmersion.api.catalogue.Year, end: com.histmersion.api.catalogue.Year): com.histmersion.api.catalogue.Year = _root_.com.histmersion.api.catalogue.Year((start).value + Math.floorDiv((end).value - (start).value, 2L))

  def timeTime(rows: List[TreeRow], from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year, depth: com.histmersion.api.library.Tree.TimeTimeDepth, samples: com.histmersion.api.library.Tree.TimeTimeSamples = _root_.com.histmersion.api.library.Tree.TimeTimeSamples(0)): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(rows, lo, hi, Aggregate.bucketSize(Aggregate.Span(hi.value - lo.value)), clamp(depth), samples, (_, _, _) => (Nil, Bucket.Overlap(0)))

  def timeTree(rows: List[TreeRow], from: com.histmersion.api.library.Tree.TimeTreeFrom, to: com.histmersion.api.library.Tree.TimeTreeTo, depth: com.histmersion.api.library.Tree.TimeTreeDepth, samples: com.histmersion.api.library.Tree.TimeTreeSamples = _root_.com.histmersion.api.library.Tree.TimeTreeSamples(0)): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  private def timeLevel(
      rows: List[TreeRow],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      size: Aggregate.Width,
      depth: com.histmersion.api.library.Tree.Depth,
      samples: com.histmersion.api.library.Tree.TimeLevelSamples,
      nest: (List[TreeRow], com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year) => (List[Bucket], Bucket.Overlap)
  ): List[Bucket] =
    val finer = Aggregate.finer(size)
    Aggregate.buckets(from, to, size).flatMap { (bStart, bEnd) =>
      val mine = rows.filter(overlaps(_, bStart, bEnd))
      if mine.isEmpty then Nil
      else
        val count = distinct(mine)
        val (kids, over) =
          if depth.value <= 1 then (Nil, Bucket.Overlap(0))
          else
            nest(mine, bStart, bEnd) match
              case (Nil, _) if finer.value < size.value =>
                (splitWorthShowing(count, timeLevel(mine, bStart, bEnd, finer, Depth(depth.value - 1), samples, nest)), Bucket.Overlap(0))
              case (Nil, _) => (Nil, Bucket.Overlap(0))
              case (ks, o)  => (ks, o)
        Some(
          Bucket(
            label = Bucket.Label(YearFmt.period(Some(bStart), Some(bEnd))),
            key = Bucket.Key(s"$KindTime:$bStart:$bEnd"),
            kind = Bucket.Kind(KindTime),
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

  def splitWorthShowing(parent: Bucket.Count, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists(_.count.value < parent.value * SplitRatio.value) then kids else Nil

  def regionTree(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionTreeAsOf],
      depth: com.histmersion.api.library.Tree.RegionTreeDepth,
      samples: com.histmersion.api.library.Tree.RegionTreeSamples = _root_.com.histmersion.api.library.Tree.RegionTreeSamples(0),
      root: Option[com.histmersion.api.library.Tree.RegionTreeRoot] = None
  ): List[Bucket] =
    regions(rows, nodes, relations, asOf, clamp(depth), samples, (_, _) => Nil, root)._1

  def timeRegionTree(
      rows: List[TreeRow],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      depth: com.histmersion.api.library.Tree.TimeRegionTreeDepth,
      samples: com.histmersion.api.library.Tree.TimeRegionTreeSamples = _root_.com.histmersion.api.library.Tree.TimeRegionTreeSamples(0)
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(
      rows,
      lo,
      hi,
      Aggregate.bucketSize(Aggregate.Span(hi.value - lo.value)),
      _root_.com.histmersion.api.library.Tree.Depth(2),
      samples,
      (mine, bStart, bEnd) =>
        regions(mine, nodes, relations, Some(midpoint(bStart, bEnd)), clamp(depth), samples, (_, _) => Nil)
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
      clamp(depth),
      samples,
      (mine, _) => timeTime(mine, _root_.com.histmersion.api.catalogue.Year((lo).value), _root_.com.histmersion.api.catalogue.Year((hi).value), _root_.com.histmersion.api.library.Tree.Depth(1), samples),
      root
    )._1

  private def regions(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionsAsOf],
      depth: com.histmersion.api.library.Tree.Depth,
      samples: com.histmersion.api.library.Tree.MaxSamplesCount,
      leaf: (List[TreeRow], RegionNode.Id) => List[Bucket],
      root: Option[RegionNode.Id] = None
  ): (List[Bucket], Bucket.Overlap) =
    val byId = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: RegionNode.Id) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges = relations.filter(r =>
      r.kind == _root_.com.histmersion.api.catalogue.Place.Relation.Kind(Place.Relation.PartOf) && coversAsOf(r.period, asOf) && live(childNode(r.childId)) && live(parentNode(r.parentId))
    )
    val kidsOf = edges.groupMap(r => parentNode(r.parentId))(r => childNode(r.childId)).view.mapValues(_.distinct).toMap
    val parents = edges.groupMap(r => childNode(r.childId))(r => parentNode(r.parentId)).view.mapValues(_.distinct).toMap

    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: RegionNode.Id): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    val drawnAt = scala.collection.mutable.Map.empty[RegionNode.Id, Unit]

    def level(ids: List[RegionNode.Id], seen: Set[RegionNode.Id], left: Depth): List[Bucket] =
      ids.distinct
        .filterNot(seen.contains)
        .map(id => (id, at(id)))
        .filter((_, mine) => mine.nonEmpty)
        .sortBy((id, mine) => (-(distinct(mine).value), byId.get(id).fold("")(_.label.value.toLowerCase), id.value: String))
        .flatMap { (id, mine) =>
          if drawnAt.contains(id) then None
          else
            drawnAt.update(id, ())
            val node = byId(id)
            val allKids = kidsOf.getOrElse(id, Nil).filterNot(seen.contains)
            val kids =
              if left.value <= 1 then leaf(mine, id)
              else
                level(allKids, seen + id, Depth(left.value - 1)) match
                  case Nil => leaf(mine, id)
                  case ks  => ks
            val elsewhere = parents
              .getOrElse(id, Nil)
              .filterNot(seen.contains)
              .flatMap(byId.get)
              .filter(p => coversAsOf(p.period, asOf))
              .map(p => Bucket.AlsoUnder(p.label.value))
              .distinct
            Some(
              Bucket(
                label = Bucket.Label(node.label.value),
                key = Bucket.Key(node.id.value),
                kind = Bucket.Kind(KindRegion),
                start = node.period.map(_.start),
                end = node.period.map(_.end),
                placeId = Some(Place.Id(node.id.value)),
                count = distinct(mine),
                overlap = overlapOf(mine, allKids.map(at)),
                contested = Bucket.Contested(node.contested.value),
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
          label = Bucket.Label(KeyUnlocated),
          key = Bucket.Key(KeyUnlocated),
          kind = Bucket.Kind(KindRegion),
          count = distinct(lost),
          samples = sampleOf(lost, samples)
        )
    (roots, overlapOf(rows, rootIds.map(at)))

  private def rendered(buckets: List[Bucket]): Set[RegionNode.Id] =
    buckets.iterator.flatMap(b => b.placeId.map(p => RegionNode.Id(p.value)).toSet ++ rendered(b.children)).toSet

  private def childNode(id: Place.Relation.ChildId): RegionNode.Id = RegionNode.Id(id.value)
  private def parentNode(id: Place.Relation.ParentId): RegionNode.Id = RegionNode.Id(id.value)

  private def ancestors(start: Set[RegionNode.Id], parents: Map[RegionNode.Id, List[RegionNode.Id]]): Set[RegionNode.Id] =
    @annotation.tailrec
    def go(frontier: Set[RegionNode.Id], seen: Set[RegionNode.Id]): Set[RegionNode.Id] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  private def distinct(rows: List[TreeRow]): Bucket.Count = Bucket.Count(rows.iterator.map(_.key).toSet.size)

  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): Bucket.Overlap =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then Bucket.Overlap(0)
    else Bucket.Overlap(rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1))

  private def sampleOf(rows: List[TreeRow], n: com.histmersion.api.library.Tree.MaxSamplesCount): List[Bucket.Sample] =
    if n.value <= 0 then Nil
    else rows.iterator.map(r => Bucket.Sample(r.citekey, r.title)).distinctBy(_.citekey).take((n).value).toList

  private def clamp(depth: com.histmersion.api.library.Tree.Depth): com.histmersion.api.library.Tree.Depth = _root_.com.histmersion.api.library.Tree.Depth((depth).value.max(1).min((MaxDepth).value))
  private def order(a: com.histmersion.api.catalogue.Year, b: com.histmersion.api.catalogue.Year): (com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year) = if a.value <= b.value then (a, b) else (b, a)

  def parseBy(s: String): Option[List[String]] =
    val parts = (s).split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some((parts).map(refactorValue11 => (refactorValue11)))
    else None
