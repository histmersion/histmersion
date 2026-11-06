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

  def buckets(from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year, size: com.histmersion.api.library.Aggregate.Width): List[(com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year)] =
    require((size).value > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(from, to)
    val first = Math.floorDiv(lo.value, size.value) * size.value
    // Not `Iterator.iterate(_ + size)`: near Long.MaxValue the addition wraps negative and the iterator never ends.
    val out = List.newBuilder[(Long, Long)]
    var s = first
    var go = s <= hi.value
    while go do
      val e = try Math.addExact(s, (size).value - 1)
      catch case _: ArithmeticException => Long.MaxValue
      out += ((s, e))
      if e == Long.MaxValue then go = false
      else
        s = e + 1
        go = s <= hi.value
    out.result().map((a, b) => (_root_.com.histmersion.api.catalogue.Year(a), _root_.com.histmersion.api.catalogue.Year(b)))

  def bucketCount(from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year, size: com.histmersion.api.library.Aggregate.Width): BigInt =
    if size.value <= 0 then BigInt(0)
    else
      val (lo, hi) = order(from, to)
      val first = BigInt(Math.floorDiv(lo.value, size.value)) * size.value
      ((BigInt(hi.value) - first) / size.value) + 1

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

  def histogram(rows: List[Period.Row], from: com.histmersion.api.catalogue.Year, to: com.histmersion.api.catalogue.Year): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(_root_.com.histmersion.api.library.Aggregate.Span(hi.value - lo.value)))

  def histogram(
      rows: List[Period.Row],
      from: com.histmersion.api.catalogue.Year,
      to: com.histmersion.api.catalogue.Year,
      size: com.histmersion.api.library.Aggregate.Width,
      counting: Counting = Counting.Overlap
  ): List[TimeBucket] =
    val spans = rows.flatMap(span)
    counting match
      case Counting.Overlap =>
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(TimeBucket.Start(bStart.value), TimeBucket.End(bEnd.value), TimeBucket.Count(spans.count((s, e) => s.value <= bEnd.value && e.value >= bStart.value)))
        }
      case Counting.Starts =>
        val (lo, hi) = order(from, to)
        val byBucket = spans.iterator
          .map(_._1)
          .filter(y => y.value >= lo.value && y.value <= hi.value)
          .map(y => Math.floorDiv(y.value, size.value) * size.value)
          .toList
          .groupBy(identity)
          .view
          .mapValues(_.size)
          .toMap
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(TimeBucket.Start(bStart.value), TimeBucket.End(bEnd.value), TimeBucket.Count(byBucket.getOrElse(bStart.value, 0)))
        }

  def span(row: Period.Row): Option[(com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: com.histmersion.api.catalogue.Year, b: com.histmersion.api.catalogue.Year): (com.histmersion.api.catalogue.Year, com.histmersion.api.catalogue.Year) = if a.value <= b.value then (a, b) else (b, a)
