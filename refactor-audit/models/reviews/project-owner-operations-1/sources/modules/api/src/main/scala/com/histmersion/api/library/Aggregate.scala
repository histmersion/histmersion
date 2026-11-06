package com.histmersion.api.library

import com.histmersion.api.given
import com.histmersion.api.catalogue.Period
object Aggregate:

  type BucketSizeResult = BucketSizeResult.T
  object BucketSizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketSizeSpanYears = BucketSizeSpanYears.T
  object BucketSizeSpanYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinerResult = FinerResult.T
  object FinerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FinerSize = FinerSize.T
  object FinerSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketsResultPart1 = BucketsResultPart1.T
  object BucketsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketsResultPart2 = BucketsResultPart2.T
  object BucketsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketsFrom = BucketsFrom.T
  object BucketsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketsTo = BucketsTo.T
  object BucketsTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketsSize = BucketsSize.T
  object BucketsSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketCountFrom = BucketCountFrom.T
  object BucketCountFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketCountTo = BucketCountTo.T
  object BucketCountTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BucketCountSize = BucketCountSize.T
  object BucketCountSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HistogramFrom = HistogramFrom.T
  object HistogramFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HistogramTo = HistogramTo.T
  object HistogramTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HistogramSize = HistogramSize.T
  object HistogramSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanResultPart1 = SpanResultPart1.T
  object SpanResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanResultPart2 = SpanResultPart2.T
  object SpanResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResultPart1 = OrderResultPart1.T
  object OrderResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderResultPart2 = OrderResultPart2.T
  object OrderResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderA = OrderA.T
  object OrderA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderB = OrderB.T
  object OrderB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def bucketSize(spanYears: com.histmersion.api.library.Aggregate.BucketSizeSpanYears): com.histmersion.api.library.Aggregate.BucketSizeResult =
    val span = math.abs((spanYears).value)
    if span >= 20000 then _root_.com.histmersion.api.library.Aggregate.BucketSizeResult((5000).toLong)
    else if span >= 5000 then _root_.com.histmersion.api.library.Aggregate.BucketSizeResult((1000).toLong)
    else if span >= 1000 then _root_.com.histmersion.api.library.Aggregate.BucketSizeResult((100).toLong)
    else _root_.com.histmersion.api.library.Aggregate.BucketSizeResult((10).toLong)

  def finer(size: com.histmersion.api.library.Aggregate.FinerSize): com.histmersion.api.library.Aggregate.FinerResult = size match
    case s if (s).value > 1000 => _root_.com.histmersion.api.library.Aggregate.FinerResult((1000).toLong)
    case s if (s).value > 100  => _root_.com.histmersion.api.library.Aggregate.FinerResult((100).toLong)
    case _             => _root_.com.histmersion.api.library.Aggregate.FinerResult((10).toLong)

  def buckets(from: com.histmersion.api.library.Aggregate.BucketsFrom, to: com.histmersion.api.library.Aggregate.BucketsTo, size: com.histmersion.api.library.Aggregate.BucketsSize): List[(com.histmersion.api.library.Aggregate.BucketsResultPart1, com.histmersion.api.library.Aggregate.BucketsResultPart2)] =
    require((size).value > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(from, to)
    val first = Math.floorDiv(lo, size) * size
    // Not `Iterator.iterate(_ + size)`: near Long.MaxValue the addition wraps negative and the iterator never ends.
    val out = List.newBuilder[(Long, Long)]
    var s = first
    var go = s <= hi
    while go do
      val e = try Math.addExact(s, (size).value - 1)
      catch case _: ArithmeticException => Long.MaxValue
      out += ((s, e))
      if e == Long.MaxValue then go = false
      else
        s = e + 1
        go = s <= hi
    (out.result()).map(refactorValue => { val refactorTuple = refactorValue; (_root_.com.histmersion.api.library.Aggregate.BucketsResultPart1(refactorTuple._1), _root_.com.histmersion.api.library.Aggregate.BucketsResultPart2(refactorTuple._2)) })

  def bucketCount(from: com.histmersion.api.library.Aggregate.BucketCountFrom, to: com.histmersion.api.library.Aggregate.BucketCountTo, size: com.histmersion.api.library.Aggregate.BucketCountSize): BigInt =
    if size <= 0 then BigInt(0)
    else
      val (lo, hi) = order(from, to)
      val first = BigInt(Math.floorDiv(lo, size)) * size
      ((BigInt(hi) - first) / size) + 1

  enum Counting:
    case Overlap, Starts

  object Counting:
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.library.Aggregate.Counting.ParseS): Option[Counting] = (s).value.trim.toLowerCase match
      case "starts"       => Some(Starts)
      case "overlap" | "" => Some(Overlap)
      case _              => None

  val UnitStarts = "starts"

  def histogram(rows: List[Period.Row], from: com.histmersion.api.library.Aggregate.HistogramFrom, to: com.histmersion.api.library.Aggregate.HistogramTo): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(hi - lo))

  def histogram(
      rows: List[Period.Row],
      from: com.histmersion.api.library.Aggregate.HistogramFrom,
      to: com.histmersion.api.library.Aggregate.HistogramTo,
      size: com.histmersion.api.library.Aggregate.HistogramSize,
      counting: Counting = Counting.Overlap
  ): List[TimeBucket] =
    val spans = rows.flatMap(span)
    counting match
      case Counting.Overlap =>
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(bStart, bEnd, spans.count((s, e) => s <= bEnd && e >= bStart))
        }
      case Counting.Starts =>
        val (lo, hi) = order(from, to)
        val byBucket = spans.iterator
          .map(_._1)
          .filter(y => y >= lo && y <= hi)
          .map(y => Math.floorDiv((y).value, (size).value) * size)
          .toList
          .groupBy(identity)
          .view
          .mapValues(_.size)
          .toMap
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(bStart, bEnd, byBucket.getOrElse(bStart, 0))
        }

  def span(row: Period.Row): Option[(com.histmersion.api.library.Aggregate.SpanResultPart1, com.histmersion.api.library.Aggregate.SpanResultPart2)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: com.histmersion.api.library.Aggregate.OrderA, b: com.histmersion.api.library.Aggregate.OrderB): (com.histmersion.api.library.Aggregate.OrderResultPart1, com.histmersion.api.library.Aggregate.OrderResultPart2) = if a <= b then (a, b) else (b, a)
