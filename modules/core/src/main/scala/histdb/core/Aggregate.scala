package histdb.core

/** The period of ONE aggregation unit as it comes out of SQL (`lower(period)`, `upper(period) - 1`).
  * For `unit=sources` there is exactly one row per source, for `unit=findings` one per finding.
  * Both bounds `None` = **undated** → ignored by every function here (plan §1.7). */
final case class PeriodRow(periodStart: Option[Long], periodEnd: Option[Long])

/** Pure time-bucketing behind `GET /api/aggregate?by=time` (plan §1.6 bucket sizes, §1.7 strip).
  *
  * No DB and no IO: the repo selects period bounds, this turns them into a histogram, so the whole
  * thing is unit-testable. Wave 2's `by=time,region` tree reuses [[bucketSize]] / [[buckets]]
  * verbatim — a tree level IS a call to [[buckets]] with the next finer size, and its leaf counts are
  * a call to [[histogram]] restricted to the level's rows. */
object Aggregate:

  /** Adaptive bucket size for a span, in years (plan §1.6):
    * span ≥ 20 k → 5 k · ≥ 5 k → 1 k · ≥ 1 k → 100 · else 10. */
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

  /** Inclusive `[start, end]` buckets of `size` years covering `[from, to]`, **aligned to multiples
    * of `size`** (so the same year always falls in the same bucket, whatever the window). */
  def buckets(from: Long, to: Long, size: Long): List[(Long, Long)] =
    require(size > 0, s"bucket size must be positive, got $size")
    val (lo, hi) = order(from, to)
    val first    = Math.floorDiv(lo, size) * size
    Iterator.iterate(first)(_ + size).takeWhile(_ <= hi).map(s => (s, s + size - 1)).toList

  /** Histogram over `[from, to]` at the adaptive size for that span. */
  def histogram(rows: List[PeriodRow], from: Long, to: Long): List[TimeBucket] =
    val (lo, hi) = order(from, to)
    histogram(rows, lo, hi, bucketSize(hi - lo))

  /** Histogram at an explicit bucket size. A row is counted in **every** bucket its period overlaps
    * (so a long-lived source contributes to several bars — counts are NOT additive across bars).
    * Undated rows are dropped. */
  def histogram(rows: List[PeriodRow], from: Long, to: Long, size: Long): List[TimeBucket] =
    val spans = rows.flatMap(span)
    buckets(from, to, size).map { (bStart, bEnd) =>
      TimeBucket(bStart, bEnd, spans.count((s, e) => s <= bEnd && e >= bStart))
    }

  /** A row's inclusive `[start, end]`, or `None` when undated. A half-dated row (one open bound,
    * e.g. an unbounded `int8range`) collapses to the known bound, i.e. a point in time. */
  def span(row: PeriodRow): Option[(Long, Long)] = (row.periodStart, row.periodEnd) match
    case (Some(a), Some(b)) => Some(order(a, b))
    case (Some(a), None)    => Some((a, a))
    case (None, Some(b))    => Some((b, b))
    case (None, None)       => None

  private def order(a: Long, b: Long): (Long, Long) = if a <= b then (a, b) else (b, a)
