package com.histmersion.api.library

import com.histmersion.api.catalogue.PeriodRow
import com.histmersion.api.given
object Aggregate:

  def bucketSize(spanYears: Long): Long =
    val span = math.abs(spanYears)
    if span >= 20000 then 5000
    else if span >= 5000 then 1000
    else if span >= 1000 then 100
    else 10

  def finer(size: Long): Long = size match
    case s if s > 1000 => 1000
    case s if s > 100  => 100
    case _             => 10

  def buckets(from: Long, to: Long, size: Long): List[(Long, Long)] =
    require(size > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(from, to)
    val first = Math.floorDiv(lo, size) * size
    // Not `Iterator.iterate(_ + size)`: near Long.MaxValue the addition wraps negative and the iterator never ends.
    val out = List.newBuilder[(Long, Long)]
    var s = first
    var go = s <= hi
    while go do
      val e = try Math.addExact(s, size - 1)
      catch case _: ArithmeticException => Long.MaxValue
      out += ((s, e))
      if e == Long.MaxValue then go = false
      else
        s = e + 1
        go = s <= hi
    out.result()

  def bucketCount(from: Long, to: Long, size: Long): BigInt =
    if size <= 0 then BigInt(0)
    else
      val (lo, hi) = order(from, to)
      val first = BigInt(Math.floorDiv(lo, size)) * size
      ((BigInt(hi) - first) / size) + 1

  enum Counting:
    case Overlap, Starts

  object Counting:
    def parse(s: String): Option[Counting] = s.trim.toLowerCase match
      case "starts"       => Some(Starts)
      case "overlap" | "" => Some(Overlap)
      case _              => None

  val UnitStarts = "starts"

  def histogram(rows: List[PeriodRow], from: Long, to: Long): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(hi - lo))

  def histogram(
      rows: List[PeriodRow],
      from: Long,
      to: Long,
      size: Long,
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
          .map(y => Math.floorDiv(y, size) * size)
          .toList
          .groupBy(identity)
          .view
          .mapValues(_.size)
          .toMap
        buckets(from, to, size).map { (bStart, bEnd) =>
          TimeBucket(bStart, bEnd, byBucket.getOrElse(bStart, 0))
        }

  def span(row: PeriodRow): Option[(Long, Long)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: Long, b: Long): (Long, Long) = if a <= b then (a, b) else (b, a)
