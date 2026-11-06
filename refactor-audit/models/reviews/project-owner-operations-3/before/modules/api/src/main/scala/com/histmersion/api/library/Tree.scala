package com.histmersion.api.library
import com.histmersion.api.Markdown
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

object Tree:

  type RegionsLiveId = RegionsLiveId.T
  object RegionsLiveId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsAtId = RegionsAtId.T
  object RegionsAtId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsLevelIds = RegionsLevelIds.T
  object RegionsLevelIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsLevelSeen = RegionsLevelSeen.T
  object RegionsLevelSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsLevelLeft = RegionsLevelLeft.T
  object RegionsLevelLeft extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsGoResult = AncestorsGoResult.T
  object AncestorsGoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsGoFrontier = AncestorsGoFrontier.T
  object AncestorsGoFrontier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsGoSeen = AncestorsGoSeen.T
  object AncestorsGoSeen extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxDepth = MaxDepth.T
  object MaxDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxSamples = MaxSamples.T
  object MaxSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoversAsOfResult = CoversAsOfResult.T
  object CoversAsOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoversAsOfAsOf = CoversAsOfAsOf.T
  object CoversAsOfAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsResult = OverlapsResult.T
  object OverlapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsFrom = OverlapsFrom.T
  object OverlapsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsTo = OverlapsTo.T
  object OverlapsTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidpointResult = MidpointResult.T
  object MidpointResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidpointStart = MidpointStart.T
  object MidpointStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidpointEnd = MidpointEnd.T
  object MidpointEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTimeFrom = TimeTimeFrom.T
  object TimeTimeFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTimeTo = TimeTimeTo.T
  object TimeTimeTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTimeDepth = TimeTimeDepth.T
  object TimeTimeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTimeSamples = TimeTimeSamples.T
  object TimeTimeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTreeFrom = TimeTreeFrom.T
  object TimeTreeFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTreeTo = TimeTreeTo.T
  object TimeTreeTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTreeDepth = TimeTreeDepth.T
  object TimeTreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeTreeSamples = TimeTreeSamples.T
  object TimeTreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelFrom = TimeLevelFrom.T
  object TimeLevelFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelTo = TimeLevelTo.T
  object TimeLevelTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelSize = TimeLevelSize.T
  object TimeLevelSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelDepth = TimeLevelDepth.T
  object TimeLevelDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelSamples = TimeLevelSamples.T
  object TimeLevelSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelNestArg2 = TimeLevelNestArg2.T
  object TimeLevelNestArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelNestArg3 = TimeLevelNestArg3.T
  object TimeLevelNestArg3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeLevelNestResultPart2 = TimeLevelNestResultPart2.T
  object TimeLevelNestResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitRatio = SplitRatio.T
  object SplitRatio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitWorthShowingParent = SplitWorthShowingParent.T
  object SplitWorthShowingParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTreeAsOf = RegionTreeAsOf.T
  object RegionTreeAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTreeDepth = RegionTreeDepth.T
  object RegionTreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTreeSamples = RegionTreeSamples.T
  object RegionTreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTreeRoot = RegionTreeRoot.T
  object RegionTreeRoot extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRegionTreeFrom = TimeRegionTreeFrom.T
  object TimeRegionTreeFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRegionTreeTo = TimeRegionTreeTo.T
  object TimeRegionTreeTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRegionTreeDepth = TimeRegionTreeDepth.T
  object TimeRegionTreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRegionTreeSamples = TimeRegionTreeSamples.T
  object TimeRegionTreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeFrom = RegionTimeTreeFrom.T
  object RegionTimeTreeFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeTo = RegionTimeTreeTo.T
  object RegionTimeTreeTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeAsOf = RegionTimeTreeAsOf.T
  object RegionTimeTreeAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeDepth = RegionTimeTreeDepth.T
  object RegionTimeTreeDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeSamples = RegionTimeTreeSamples.T
  object RegionTimeTreeSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionTimeTreeRoot = RegionTimeTreeRoot.T
  object RegionTimeTreeRoot extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsResultPart2 = RegionsResultPart2.T
  object RegionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsAsOf = RegionsAsOf.T
  object RegionsAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsDepth = RegionsDepth.T
  object RegionsDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsSamples = RegionsSamples.T
  object RegionsSamples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsLeafArg2 = RegionsLeafArg2.T
  object RegionsLeafArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionsRoot = RegionsRoot.T
  object RegionsRoot extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenderedResult = RenderedResult.T
  object RenderedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsResult = AncestorsResult.T
  object AncestorsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsStart = AncestorsStart.T
  object AncestorsStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsParentsPart1 = AncestorsParentsPart1.T
  object AncestorsParentsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AncestorsParentsPart2 = AncestorsParentsPart2.T
  object AncestorsParentsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistinctResult = DistinctResult.T
  object DistinctResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapOfResult = OverlapOfResult.T
  object OverlapOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SampleOfN = SampleOfN.T
  object SampleOfN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampResult = ClampResult.T
  object ClampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClampDepth = ClampDepth.T
  object ClampDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResultPart1 = OrderResultPart1.T
  object OrderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResultPart2 = OrderResultPart2.T
  object OrderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderA = OrderA.T
  object OrderA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderB = OrderB.T
  object OrderB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseByResult = ParseByResult.T
  object ParseByResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseByS = ParseByS.T
  object ParseByS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MaxDepth: com.histmersion.api.library.Tree.MaxDepth = _root_.com.histmersion.api.library.Tree.MaxDepth(3)

  val MaxSamples: com.histmersion.api.library.Tree.MaxSamples = _root_.com.histmersion.api.library.Tree.MaxSamples(5)

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

  def coversAsOf(p: Option[Period], asOf: Option[com.histmersion.api.library.Tree.CoversAsOfAsOf]): com.histmersion.api.library.Tree.CoversAsOfResult = (p, asOf) match
    case (None, _)                     => _root_.com.histmersion.api.library.Tree.CoversAsOfResult(true)
    case (_, None)                     => _root_.com.histmersion.api.library.Tree.CoversAsOfResult(true)
    case (Some(Period(a, b)), Some(y)) => y >= a && y <= b

  private def overlaps(row: TreeRow, from: com.histmersion.api.library.Tree.OverlapsFrom, to: com.histmersion.api.library.Tree.OverlapsTo): com.histmersion.api.library.Tree.OverlapsResult =
    Aggregate.span(row.toPeriodRow).exists((a, b) => a <= to && b >= from)

  def midpoint(start: com.histmersion.api.library.Tree.MidpointStart, end: com.histmersion.api.library.Tree.MidpointEnd): com.histmersion.api.library.Tree.MidpointResult = start + Math.floorDiv(end - start, 2L)

  def timeTime(rows: List[TreeRow], from: com.histmersion.api.library.Tree.TimeTimeFrom, to: com.histmersion.api.library.Tree.TimeTimeTo, depth: com.histmersion.api.library.Tree.TimeTimeDepth, samples: com.histmersion.api.library.Tree.TimeTimeSamples = _root_.com.histmersion.api.library.Tree.TimeTimeSamples(0)): List[Bucket] =
    val (lo, hi) = order(from, to)
    timeLevel(rows, lo, hi, Aggregate.bucketSize(hi - lo), clamp(depth), samples, (_, _, _) => (Nil, _root_.com.histmersion.api.library.Tree.TimeLevelNestResultPart2(0)))

  def timeTree(rows: List[TreeRow], from: com.histmersion.api.library.Tree.TimeTreeFrom, to: com.histmersion.api.library.Tree.TimeTreeTo, depth: com.histmersion.api.library.Tree.TimeTreeDepth, samples: com.histmersion.api.library.Tree.TimeTreeSamples = _root_.com.histmersion.api.library.Tree.TimeTreeSamples(0)): List[Bucket] =
    timeTime(rows, from, to, depth, samples)

  private def timeLevel(
      rows: List[TreeRow],
      from: com.histmersion.api.library.Tree.TimeLevelFrom,
      to: com.histmersion.api.library.Tree.TimeLevelTo,
      size: com.histmersion.api.library.Tree.TimeLevelSize,
      depth: com.histmersion.api.library.Tree.TimeLevelDepth,
      samples: com.histmersion.api.library.Tree.TimeLevelSamples,
      nest: (List[TreeRow], com.histmersion.api.library.Tree.TimeLevelNestArg2, com.histmersion.api.library.Tree.TimeLevelNestArg3) => (List[Bucket], com.histmersion.api.library.Tree.TimeLevelNestResultPart2)
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

  val SplitRatio: com.histmersion.api.library.Tree.SplitRatio = _root_.com.histmersion.api.library.Tree.SplitRatio(0.8)

  def splitWorthShowing(parent: com.histmersion.api.library.Tree.SplitWorthShowingParent, kids: List[Bucket]): List[Bucket] =
    if kids.sizeIs > 1 && kids.exists(_.count < parent * SplitRatio) then kids else Nil

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
      from: com.histmersion.api.library.Tree.TimeRegionTreeFrom,
      to: com.histmersion.api.library.Tree.TimeRegionTreeTo,
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
      Aggregate.bucketSize(hi - lo),
      _root_.com.histmersion.api.library.Tree.TimeLevelDepth(2),
      samples,
      (mine, bStart, bEnd) =>
        regions(mine, nodes, relations, Some(midpoint(bStart, bEnd)), clamp(depth), samples, (_, _) => Nil)
    )

  def regionTimeTree(
      rows: List[TreeRow],
      from: com.histmersion.api.library.Tree.RegionTimeTreeFrom,
      to: com.histmersion.api.library.Tree.RegionTimeTreeTo,
      nodes: List[RegionNode],
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionTimeTreeAsOf],
      depth: com.histmersion.api.library.Tree.RegionTimeTreeDepth,
      samples: com.histmersion.api.library.Tree.RegionTimeTreeSamples = _root_.com.histmersion.api.library.Tree.RegionTimeTreeSamples(0),
      root: Option[com.histmersion.api.library.Tree.RegionTimeTreeRoot] = None
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
      relations: List[Place.Relation],
      asOf: Option[com.histmersion.api.library.Tree.RegionsAsOf],
      depth: com.histmersion.api.library.Tree.RegionsDepth,
      samples: com.histmersion.api.library.Tree.RegionsSamples,
      leaf: (List[TreeRow], com.histmersion.api.library.Tree.RegionsLeafArg2) => List[Bucket],
      root: Option[com.histmersion.api.library.Tree.RegionsRoot] = None
  ): (List[Bucket], com.histmersion.api.library.Tree.RegionsResultPart2) =
    val byId = nodes.iterator.map(n => n.id -> n).toMap
    def live(id: com.histmersion.api.library.Tree.RegionsLiveId) = byId.get(id).exists(n => coversAsOf(n.period, asOf))
    val edges = relations.filter(r =>
      r.kind == Place.Relation.PartOf && coversAsOf(r.period, asOf) && live(r.childId) && live(r.parentId)
    )
    val kidsOf = edges.groupMap(_.parentId)(_.childId).view.mapValues(_.distinct).toMap
    val parents = edges.groupMap(_.childId)(_.parentId).view.mapValues(_.distinct).toMap

    val reach = rows.iterator.map(r => r.key -> ancestors(r.placeIds.filter(live).toSet, parents)).toMap
    def at(id: com.histmersion.api.library.Tree.RegionsAtId): List[TreeRow] = rows.filter(r => reach(r.key).contains(id))

    val drawnAt = scala.collection.mutable.Map.empty[String, Unit]

    def level(ids: List[com.histmersion.api.library.Tree.RegionsLevelIds], seen: Set[com.histmersion.api.library.Tree.RegionsLevelSeen], left: com.histmersion.api.library.Tree.RegionsLevelLeft): List[Bucket] =
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

  private def rendered(buckets: List[Bucket]): Set[com.histmersion.api.library.Tree.RenderedResult] =
    buckets.iterator.flatMap(b => b.placeId.toSet ++ rendered(b.children)).toSet

  private def ancestors(start: Set[com.histmersion.api.library.Tree.AncestorsStart], parents: Map[com.histmersion.api.library.Tree.AncestorsParentsPart1, List[com.histmersion.api.library.Tree.AncestorsParentsPart2]]): Set[com.histmersion.api.library.Tree.AncestorsResult] =
    @annotation.tailrec
    def go(frontier: Set[com.histmersion.api.library.Tree.AncestorsGoFrontier], seen: Set[com.histmersion.api.library.Tree.AncestorsGoSeen]): Set[com.histmersion.api.library.Tree.AncestorsGoResult] =
      val next = frontier.flatMap(parents.getOrElse(_, Nil)) -- seen
      if next.isEmpty then seen else go(next, seen ++ next)
    go(start, start)

  private def distinct(rows: List[TreeRow]): com.histmersion.api.library.Tree.DistinctResult = _root_.com.histmersion.api.library.Tree.DistinctResult(rows.iterator.map(_.key).toSet.size)

  private def overlapOf(rows: List[TreeRow], groups: List[List[TreeRow]]): com.histmersion.api.library.Tree.OverlapOfResult =
    val sets = groups.map(_.iterator.map(_.key).toSet).filter(_.nonEmpty)
    if sets.sizeIs < 2 then _root_.com.histmersion.api.library.Tree.OverlapOfResult(0)
    else _root_.com.histmersion.api.library.Tree.OverlapOfResult(rows.iterator.map(_.key).toSet.count(k => sets.count(_.contains(k)) > 1))

  private def sampleOf(rows: List[TreeRow], n: com.histmersion.api.library.Tree.SampleOfN): List[Bucket.Sample] =
    if n <= 0 then Nil
    else rows.iterator.map(r => Bucket.Sample(r.citekey, r.title)).distinctBy(_.citekey).take(n).toList

  private def clamp(depth: com.histmersion.api.library.Tree.ClampDepth): com.histmersion.api.library.Tree.ClampResult = depth.max(1).min(MaxDepth)
  private def order(a: com.histmersion.api.library.Tree.OrderA, b: com.histmersion.api.library.Tree.OrderB): (com.histmersion.api.library.Tree.OrderResultPart1, com.histmersion.api.library.Tree.OrderResultPart2) = if a <= b then (a, b) else (b, a)

  def parseBy(s: com.histmersion.api.library.Tree.ParseByS): Option[List[com.histmersion.api.library.Tree.ParseByResult]] =
    val parts = s.split(",").map(_.trim.toLowerCase).filter(_.nonEmpty).toList
    if parts.nonEmpty && parts.forall(p => p == KindTime || p == KindRegion) && parts == parts.distinct
    then Some(parts)
    else None
