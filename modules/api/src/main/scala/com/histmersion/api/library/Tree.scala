package com.histmersion
package api.library
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

object Tree:
  export TimeHistogram.Depth
  type MaxSamplesCount = MaxSamplesCount.T
  object MaxSamplesCount extends Newtype[Int, Pure]
  type SplitRatioAmount = SplitRatioAmount.T
  object SplitRatioAmount extends Newtype[Double, Pure]

  type MaxDepth = MaxDepthValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MaxDepthValue = Depth
  val MaxDepthValue: Depth.type = Depth
  type MaxSamples = MaxSamplesValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MaxSamplesValue = MaxSamplesCount
  val MaxSamplesValue: MaxSamplesCount.type = MaxSamplesCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeTimeDepth = Depth
  val TimeTimeDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeTimeSamples = MaxSamplesCount
  val TimeTimeSamples: MaxSamplesCount.type = MaxSamplesCount
  export com.histmersion.api.catalogue.{Year as TimeTreeFrom, Year as TimeTreeTo}
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeTreeDepth = Depth
  val TimeTreeDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeTreeSamples = MaxSamplesCount
  val TimeTreeSamples: MaxSamplesCount.type = MaxSamplesCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeLevelSamples = MaxSamplesCount
  val TimeLevelSamples: MaxSamplesCount.type = MaxSamplesCount
  type SplitRatio = SplitRatioValue
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type SplitRatioValue = SplitRatioAmount
  val SplitRatioValue: SplitRatioAmount.type = SplitRatioAmount
  export com.histmersion.api.catalogue.Year as RegionTreeAsOf
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionTreeDepth = Depth
  val RegionTreeDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionTreeSamples = MaxSamplesCount
  val RegionTreeSamples: MaxSamplesCount.type = MaxSamplesCount
  export RegionNode.Id as RegionTreeRoot
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeRegionTreeDepth = Depth
  val TimeRegionTreeDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type TimeRegionTreeSamples = MaxSamplesCount
  val TimeRegionTreeSamples: MaxSamplesCount.type = MaxSamplesCount
  export com.histmersion.api.catalogue.Year as RegionTimeTreeAsOf
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionTimeTreeDepth = Depth
  val RegionTimeTreeDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionTimeTreeSamples = MaxSamplesCount
  val RegionTimeTreeSamples: MaxSamplesCount.type = MaxSamplesCount
  export RegionNode.Id as RegionTimeTreeRoot
  export com.histmersion.api.catalogue.Year as RegionsAsOf
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionsDepth = Depth
  val RegionsDepth: Depth.type = Depth
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type RegionsSamples = MaxSamplesCount
  val RegionsSamples: MaxSamplesCount.type = MaxSamplesCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type SampleOfN = MaxSamplesCount
  val SampleOfN: MaxSamplesCount.type = MaxSamplesCount

  val MaxDepth: MaxDepth = Depth(3)

  val MaxSamples: MaxSamples = MaxSamplesCount(5)

  val KindTime = "time"
  val KindRegion = "region"
  val KeyUnlocated = "unlocated"

  @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
  final case class TreeRow(
      key: TreeRow.Key,
      citekey: Citekey,
      title: Title,
      start: Option[Year] = None,
      end: Option[Year] = None,
      placeIds: List[RegionNode.Id] = Nil
  ):
    def toPeriodRow: Period.Row = Period.Row(start, end)

  object TreeRow:
    type Key = Key.T
    object Key extends Newtype[String, Pure]

  def coversAsOf(p: Option[Period], asOf: Option[Year]): Boolean = (p, asOf) match
    case (None, _)                     => (true)
    case (_, None)                     => (true)
    case (Some(Period(a, b)), Some(y)) => y >= a && y <= b

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def overlaps(row: TreeRow, from: Year, to: Year): Boolean =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a <= to && b >= from)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def midpoint(start: Year, end: Year): Year = Year((start).value + Math.floorDiv((end).value - (start).value, 2L))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def timeTime(
      rows: List[TreeRow],
      from: Year,
      to: Year,
      depth: TimeTimeDepth,
      samples: TimeTimeSamples = TimeTimeSamples(0)
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(
      rows,
      lo,
      hi,
      Aggregate.bucketSize(Aggregate.Span(hi.value - lo.value)),
      clamp(depth),
      samples,
      (_, _, _) => (Nil, Bucket.Overlap(0))
    )

  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def timeTree(
      rows: List[TreeRow],
      from: Tree.TimeTreeFrom,
      to: Tree.TimeTreeTo,
      depth: TimeTreeDepth,
      samples: TimeTreeSamples = TimeTreeSamples(0)
  ): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def timeLevel(
      rows: List[TreeRow],
      from: Year,
      to: Year,
      size: Aggregate.Width,
      depth: Depth,
      samples: TimeLevelSamples,
      nest: (List[TreeRow], Year, Year) => (List[Bucket], Bucket.Overlap)
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
              case (Nil, _) if finer < size =>
                (
                  splitWorthShowing(count, timeLevel(mine, bStart, bEnd, finer, Depth(depth.value - 1), samples, nest)),
                  Bucket.Overlap(0)
                )
              case (Nil, _) => (Nil, Bucket.Overlap(0))
              case (ks, o)  => (ks, o)
        Some(
          Bucket(
            label = Bucket.Label(Year.period(Some(bStart), Some(bEnd))),
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

  val SplitRatio: SplitRatio = SplitRatioAmount(0.8)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def splitWorthShowing(parent: Bucket.Count, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists(_.count.value < parent.value * SplitRatio.value) then kids else Nil

  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def regionTree(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[Tree.RegionTreeAsOf],
      depth: RegionTreeDepth,
      samples: RegionTreeSamples = RegionTreeSamples(0),
      root: Option[Tree.RegionTreeRoot] = None
  ): List[Bucket] =
    regions(rows, nodes, relations, asOf, clamp(depth), samples, (_, _) => Nil, root)._1

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def timeRegionTree(
      rows: List[TreeRow],
      from: Year,
      to: Year,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      depth: TimeRegionTreeDepth,
      samples: TimeRegionTreeSamples = TimeRegionTreeSamples(0)
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(
      rows,
      lo,
      hi,
      Aggregate.bucketSize(Aggregate.Span(hi.value - lo.value)),
      Depth(2),
      samples,
      (mine, bStart, bEnd) =>
        regions(mine, nodes, relations, Some(midpoint(bStart, bEnd)), clamp(depth), samples, (_, _) => Nil)
    )

  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def regionTimeTree(
      rows: List[TreeRow],
      from: Year,
      to: Year,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[Tree.RegionTimeTreeAsOf],
      depth: RegionTimeTreeDepth,
      samples: RegionTimeTreeSamples = RegionTimeTreeSamples(0),
      root: Option[Tree.RegionTimeTreeRoot] = None
  ): List[Bucket] =
    val (lo, hi) = order(from, to)
    regions(
      rows,
      nodes,
      relations,
      asOf,
      clamp(depth),
      samples,
      (mine, _) => timeTime(mine, lo, hi, Depth(1), samples),
      root
    )._1

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  private def regions(
      rows: List[TreeRow],
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[Tree.RegionsAsOf],
      depth: Depth,
      samples: MaxSamplesCount,
      leaf: (List[TreeRow], RegionNode.Id) => List[Bucket],
      root: Option[RegionNode.Id] = None
  ): (List[Bucket], Bucket.Overlap) =
    val byId = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: RegionNode.Id) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges = relations.filter(r =>
      r.kind == Place.Relation.Kind(Place.Relation.PartOf) && coversAsOf(r.period, asOf) && live(
        childNode(r.childId)
      ) && live(parentNode(r.parentId))
    )
    val kidsOf = edges.groupMap(r => parentNode(r.parentId))(r => childNode(r.childId)).view.mapValues(_.distinct).toMap
    val parents =
      edges.groupMap(r => childNode(r.childId))(r => parentNode(r.parentId)).view.mapValues(_.distinct).toMap

    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: RegionNode.Id): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    val drawnAt = scala.collection.mutable.Map.empty[RegionNode.Id, Unit]

    def level(ids: List[RegionNode.Id], seen: Set[RegionNode.Id], left: Depth): List[Bucket] =
      ids.distinct
        .filterNot(seen.contains)
        .map(id => (id, at(id)))
        .filter((_, mine) => mine.nonEmpty)
        .sortBy((id, mine) =>
          (-(distinct(mine).value), byId.get(id).fold("")(_.label.value.toLowerCase), id.value: String)
        )
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
                contested = (if node.contested.contested then Bucket.Contested.Contested else Bucket.Contested.Settled),
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def rendered(buckets: List[Bucket]): Set[RegionNode.Id] =
    buckets.iterator.flatMap(b => b.placeId.map(p => RegionNode.Id(p.value)).toSet ++ rendered(b.children)).toSet

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def childNode(id: Place.Relation.ChildId): RegionNode.Id = RegionNode.Id(id.value)
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def parentNode(id: Place.Relation.ParentId): RegionNode.Id = RegionNode.Id(id.value)

  private def ancestors(
      start: Set[RegionNode.Id],
      parents: Map[RegionNode.Id, List[RegionNode.Id]]
  ): Set[RegionNode.Id] =
    @annotation.tailrec
    def go(frontier: Set[RegionNode.Id], seen: Set[RegionNode.Id]): Set[RegionNode.Id] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def distinct(rows: List[TreeRow]): Bucket.Count = Bucket.Count(rows.iterator.map(_.key).toSet.size)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): Bucket.Overlap =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then Bucket.Overlap(0)
    else Bucket.Overlap(rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sampleOf(rows: List[TreeRow], n: MaxSamplesCount): List[Bucket.Sample] =
    if n.value <= 0 then Nil
    else rows.iterator.map(r => Bucket.Sample(r.citekey, r.title)).distinctBy(_.citekey).take((n).value).toList

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def clamp(depth: Depth): Depth = Depth((depth).value.max(1).min((MaxDepth).value))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  private def order(a: Year, b: Year): (Year, Year) = if a <= b then (a, b) else (b, a)

  def parseBy(s: String): Option[List[String]] =
    val parts = (s).split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some((parts).map(refactorValue11 => (refactorValue11)))
    else None
