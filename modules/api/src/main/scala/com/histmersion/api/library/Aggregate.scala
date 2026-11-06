package com.histmersion.api.library

import com.histmersion.api.given
import com.histmersion.api.catalogue.Period
object Aggregate:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Span = Span.T
  object Span extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type HistogramFrom = _root_.com.histmersion.api.library.Tree.Year
  val HistogramFrom: _root_.com.histmersion.api.library.Tree.Year.type = _root_.com.histmersion.api.library.Tree.Year
  type HistogramTo = _root_.com.histmersion.api.library.Tree.Year
  val HistogramTo: _root_.com.histmersion.api.library.Tree.Year.type = _root_.com.histmersion.api.library.Tree.Year
  type HistogramSize = _root_.com.histmersion.api.library.Aggregate.Width
  val HistogramSize: _root_.com.histmersion.api.library.Aggregate.Width.type = _root_.com.histmersion.api.library.Aggregate.Width

  def bucketSize(spanYears: com.histmersion.api.library.Aggregate.Span): com.histmersion.api.library.Aggregate.Width =
    val span = math.abs((spanYears).value)
    if span >= 20000 then _root_.com.histmersion.api.library.Aggregate.Width((5000).toLong)
    else if span >= 5000 then _root_.com.histmersion.api.library.Aggregate.Width((1000).toLong)
    else if span >= 1000 then _root_.com.histmersion.api.library.Aggregate.Width((100).toLong)
    else _root_.com.histmersion.api.library.Aggregate.Width((10).toLong)

  def finer(size: com.histmersion.api.library.Aggregate.Width): com.histmersion.api.library.Aggregate.Width = size match
    case s if (s).value > 1000 => _root_.com.histmersion.api.library.Aggregate.Width((1000).toLong)
    case s if (s).value > 100  => _root_.com.histmersion.api.library.Aggregate.Width((100).toLong)
    case _             => _root_.com.histmersion.api.library.Aggregate.Width((10).toLong)

  def buckets(from: com.histmersion.api.library.Tree.Year, to: com.histmersion.api.library.Tree.Year, size: com.histmersion.api.library.Aggregate.Width): List[(com.histmersion.api.library.Tree.Year, com.histmersion.api.library.Aggregate.Count)] =
    require((size).value > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(_root_.com.histmersion.api.library.Tree.Year((from).value), _root_.com.histmersion.api.library.Tree.Year((to).value))
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
    (out.result()).map(refactorValue => { val refactorTuple = refactorValue; (_root_.com.histmersion.api.library.Tree.Year(refactorTuple._1), _root_.com.histmersion.api.library.Aggregate.Count(refactorTuple._2)) })

  def bucketCount(from: com.histmersion.api.library.Tree.Year, to: com.histmersion.api.library.Tree.Year, size: com.histmersion.api.library.Aggregate.Width): BigInt =
    if size <= 0 then BigInt(0)
    else
      val (lo, hi) = order(_root_.com.histmersion.api.library.Tree.Year((from).value), _root_.com.histmersion.api.library.Tree.Year((to).value))
      val first = BigInt(Math.floorDiv(lo, size)) * size
      ((BigInt(hi) - first) / size) + 1

  enum Counting:
    case Overlap, Starts

  object Counting:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.library.Aggregate.Counting.Wire): Option[Counting] = (s).value.trim.toLowerCase match
      case "starts"       => Some(Starts)
      case "overlap" | "" => Some(Overlap)
      case _              => None

  val UnitStarts = "starts"

  def histogram(rows: List[Period.Row], from: com.histmersion.api.library.Aggregate.HistogramFrom, to: com.histmersion.api.library.Aggregate.HistogramTo): List[TimeBucket] =
    val (lo, hi) = order(_root_.com.histmersion.api.library.Tree.Year((from).value), _root_.com.histmersion.api.library.Tree.Year((to).value))
    histogram(rows, _root_.com.histmersion.api.library.Tree.Year((lo).value), _root_.com.histmersion.api.library.Tree.Year((hi).value), bucketSize((hi).value - lo))

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
        val (lo, hi) = order(_root_.com.histmersion.api.library.Tree.Year((from).value), _root_.com.histmersion.api.library.Tree.Year((to).value))
        val byBucket = spans.iterator
          .map(_._1)
          .filter(y => y >= lo && y <= hi)
          .map((y => Math.floorDiv((y).value, (size).value) * (size).value).value)
          .toList
          .groupBy(identity)
          .view
          .mapValues(_.size)
          .toMap
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(bStart, bEnd, byBucket.getOrElse(bStart, 0))
        }

  def span(row: Period.Row): Option[(com.histmersion.api.library.Tree.Year, com.histmersion.api.library.Tree.Year)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: com.histmersion.api.library.Tree.Year, b: com.histmersion.api.library.Tree.Year): (com.histmersion.api.library.Tree.Year, com.histmersion.api.library.Tree.Year) = if a <= b then (_root_.com.histmersion.api.library.Tree.Year((a).value), _root_.com.histmersion.api.library.Tree.Year((b).value)) else (_root_.com.histmersion.api.library.Tree.Year((b).value), _root_.com.histmersion.api.library.Tree.Year((a).value))
