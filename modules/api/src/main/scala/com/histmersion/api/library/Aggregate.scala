package com.histmersion
package api.library

import com.histmersion.api.given
import com.histmersion.api.catalogue.Period
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
object Aggregate:
  type Count = Count.T
  object Count extends Newtype[Long, Pure]
  type Span = Span.T
  object Span extends Newtype[Long, Pure]
  type Width = Width.T
  object Width extends Newtype[Long, Pure]

  def bucketSize(spanYears: Span): Width =
    val span = math.abs((spanYears).value)
    if span >= 20000 then Width((5000).toLong)
    else if span >= 5000 then Width((1000).toLong)
    else if span >= 1000 then Width((100).toLong)
    else Width((10).toLong)

  def finer(size: Width): Width = size match
    case s if (s).value > 1000 => Width((1000).toLong)
    case s if (s).value > 100  => Width((100).toLong)
    case _             => Width((10).toLong)

  def buckets(from: Year, to: Year, size: Width): List[(Year, Year)] =
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
    out.result().map((a, b) => (Year(a), Year(b)))

  def bucketCount(from: Year, to: Year, size: Width): BigInt =
    if size.value <= 0 then BigInt(0)
    else
      val (lo, hi) = order(from, to)
      val first = BigInt(Math.floorDiv(lo.value, size.value)) * size.value
      ((BigInt(hi.value) - first) / size.value) + 1

  enum Counting:
    case Overlap, Starts

  object Counting:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Counting] = (s).value.trim.toLowerCase match
      case "starts"       => Some(Starts)
      case "overlap" | "" => Some(Overlap)
      case _              => None

  val UnitStarts = "starts"

  def histogram(rows: List[Period.Row], from: Year, to: Year): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(Span(hi.value - lo.value)))

  def histogram(
      rows: List[Period.Row],
      from: Year,
      to: Year,
      size: Width,
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

  def span(row: Period.Row): Option[(Year, Year)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: Year, b: Year): (Year, Year) = if a.value <= b.value then (a, b) else (b, a)
