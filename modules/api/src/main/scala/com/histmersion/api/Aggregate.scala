package com.histmersion.api

/** Pure time-bucketing behind `GET /api/aggregate?by=time` (plan §1.6 bucket sizes, §1.7 strip).
  *
  * No DB and no IO: the repo selects period bounds, this turns them into a histogram, so the whole thing is
  * unit-testable. Wave 2's `by=time,region` tree reuses [[bucketSize]] / [[buckets]] verbatim — a tree level IS a call
  * to [[buckets]] with the next finer size, and its leaf counts are a call to [[histogram]] restricted to the level's
  * rows.
  */
object Aggregate:

  /** Adaptive bucket size for a span, in years (plan §1.6): span ≥ 20 k → 5 k · ≥ 5 k → 1 k · ≥ 1 k → 100 · else 10.
    */
  def bucketSize(spanYears: Long): Long =
    val span = math.abs(spanYears)
    if span >= 20000 then 5000
    else if span >= 5000 then 1000
    else if span >= 1000 then 100
    else 10

  /** The next finer size below `size` (5000 → 1000 → 100 → 10 → 10). Wave 2 nests with this. */
  def finer(size: Long): Long = size match
    case s if s > 1000 => 1000
    case s if s > 100  => 100
    case _             => 10

  /** Inclusive `[start, end]` buckets of `size` years covering `[from, to]`, **aligned to multiples of `size`** (so the
    * same year always falls in the same bucket, whatever the window).
    */
  def buckets(from: Long, to: Long, size: Long): List[(Long, Long)] =
    require(size > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(from, to)
    val first = Math.floorDiv(lo, size) * size
    // Written as an overflow-SAFE loop rather than `Iterator.iterate(_ + size)`: near Long.MaxValue
    // that addition wraps to a negative number, `takeWhile(_ <= hi)` becomes true again, and the
    // iterator never ends. `to = Long.MaxValue` is one query parameter away.
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

  /** How many buckets [[buckets]] WOULD produce, without building them — so a caller can refuse an absurd window before
    * allocating it. Computed in `BigInt`, because the honest answer for `from = -Long.MaxValue, to = Long.MaxValue`
    * does not fit in a `Long`.
    */
  def bucketCount(from: Long, to: Long, size: Long): BigInt =
    if size <= 0 then BigInt(0)
    else
      val (lo, hi) = order(from, to)
      val first = BigInt(Math.floorDiv(lo, size)) * size
      ((BigInt(hi) - first) / size) + 1

  /** How a row is counted into the bars (plan §1.7).
    *
    *   - [[Overlap]] — in EVERY bucket its period touches. Honest about duration, and the right answer for "how much
    *     evidence bears on this century". But this corpus is mostly long-range syntheses: a source dated −3300…−1200
    *     lands in all 21 hundred-year bars, and once most sources do that, every bar is within a few percent of every
    *     other and the strip is a flat block that tells the reader nothing.
    *   - [[Starts]] — only in the bucket its period STARTS in. One row, one bar: the strip becomes a density of WHEN
    *     the evidence begins, which has shape, and which is how a reader reads a histogram anyway.
    *
    * Both are exact — this is a choice of question, not an approximation.
    */
  enum Counting:
    case Overlap, Starts

  object Counting:
    def parse(s: String): Option[Counting] = s.trim.toLowerCase match
      case "starts"       => Some(Starts)
      case "overlap" | "" => Some(Overlap)
      case _              => None

  /** The `unit=` spelling that asks for [[Counting.Starts]] over sources. */
  val UnitStarts = "starts"

  /** Histogram over `[from, to]` at the adaptive size for that span. */
  def histogram(rows: List[PeriodRow], from: Long, to: Long): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(hi - lo))

  /** Histogram at an explicit bucket size, counting rows as `counting` says. Undated rows are dropped either way; under
    * [[Counting.Starts]] a row whose start falls outside `[from, to]` is dropped too (its bar is off-screen — counting
    * it into the first visible bucket would invent a spike at the left edge).
    */
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

  /** A row's inclusive `[start, end]`, or `None` when undated. A half-dated row (one open bound, e.g. an unbounded
    * `int8range`) collapses to the known bound, i.e. a point in time.
    */
  def span(row: PeriodRow): Option[(Long, Long)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: Long, b: Long): (Long, Long) = if a <= b then (a, b) else (b, a)
