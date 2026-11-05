package com.histmersion.util.database

import com.histmersion.api.{Write, YearFmt}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.Api

/** The ONE spelling of a deep-time `int8range` column in SQL (#1798 item 9): how its inclusive bounds are read, how
  * optional bounds are written, and how a PATCH that names one bound merges with the stored other one.
  *
  * Postgres normalises `[a,b]` to `[a,b+1)`, so the inclusive upper bound read back is `upper − 1` (AGENTS.md — the
  * one-year-too-long trap). A bound may be open: `[a,)` is "from a", `(,b+1)` is "until b"; the open side reads back as
  * NULL. Pure, apart from the SQL text it returns.
  */
object RangeSql:

  /** `lower(col)` — the inclusive start of an `int8range` column; NULL when that side is open (Postgres reads an
    * infinite bound as NULL), or the column is NULL or empty.
    */
  type StartResult = StartResult.T
  object StartResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartCol = StartCol.T
  object StartCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndResult = EndResult.T
  object EndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EndCol = EndCol.T
  object EndCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColsResult = ColsResult.T
  object ColsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColsCol = ColsCol.T
  object ColsCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColsStartAs = ColsStartAs.T
  object ColsStartAs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ColsEndAs = ColsEndAs.T
  object ColsEndAs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeResult = RangeResult.T
  object RangeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeLo = RangeLo.T
  object RangeLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeHi = RangeHi.T
  object RangeHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsResult = OverlapsResult.T
  object OverlapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsCol = OverlapsCol.T
  object OverlapsCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsLo = OverlapsLo.T
  object OverlapsLo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlapsHi = OverlapsHi.T
  object OverlapsHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResult = BoundsResult.T
  object BoundsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsStart = BoundsStart.T
  object BoundsStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsEnd = BoundsEnd.T
  object BoundsEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AlwaysResult = AlwaysResult.T
  object AlwaysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiteralResult = LiteralResult.T
  object LiteralResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiteralStart = LiteralStart.T
  object LiteralStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiteralEnd = LiteralEnd.T
  object LiteralEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LiteralOfResult = LiteralOfResult.T
  object LiteralOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeResultPart2Part1 = MergeResultPart2Part1.T
  object MergeResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeResultPart2Part2 = MergeResultPart2Part2.T
  object MergeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeStoredPart1 = MergeStoredPart1.T
  object MergeStoredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeStoredPart2 = MergeStoredPart2.T
  object MergeStoredPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeStart = MergeStart.T
  object MergeStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeEnd = MergeEnd.T
  object MergeEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeWhat = MergeWhat.T
  object MergeWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def start(col: com.histmersion.util.database.RangeSql.StartCol): com.histmersion.util.database.RangeSql.StartResult = s"lower($col)"

  /** `upper(col) - 1` — the inclusive end (the stored bound is exclusive); NULL when that side is open. */
  def end(col: com.histmersion.util.database.RangeSql.EndCol): com.histmersion.util.database.RangeSql.EndResult = s"upper($col) - 1"

  /** `lower(col), upper(col) - 1` — the inclusive bounds of an `int8range` column, in that order. */
  def cols(col: com.histmersion.util.database.RangeSql.ColsCol): com.histmersion.util.database.RangeSql.ColsResult = s"${start(col)}, ${end(col)}"

  /** The same, each named: `lower(col) AS a, upper(col) - 1 AS b`. */
  def cols(col: com.histmersion.util.database.RangeSql.ColsCol, startAs: com.histmersion.util.database.RangeSql.ColsStartAs, endAs: com.histmersion.util.database.RangeSql.ColsEndAs): com.histmersion.util.database.RangeSql.ColsResult = s"${start(col)} AS $startAs, ${end(col)} AS $endAs"

  /** `int8range(lo, hi + 1, '[)')` — the range of two INCLUSIVE bounds given as SQL expressions (columns, `?::bigint`);
    * a NULL bound is an open side.
    */
  def range(lo: com.histmersion.util.database.RangeSql.RangeLo, hi: com.histmersion.util.database.RangeSql.RangeHi): com.histmersion.util.database.RangeSql.RangeResult = s"int8range($lo, $hi + 1, '[)')"

  /** `col && int8range(lo, hi, '[]')` — the column overlaps the inclusive window `[lo, hi]` given as SQL expressions.
    * The Scala twin is `FilterSql.overlaps`.
    */
  def overlaps(col: com.histmersion.util.database.RangeSql.OverlapsCol, lo: com.histmersion.util.database.RangeSql.OverlapsLo, hi: com.histmersion.util.database.RangeSql.OverlapsHi): com.histmersion.util.database.RangeSql.OverlapsResult = s"$col && int8range($lo, $hi, '[]')"

  /** [[overlaps]] with the window bound as two parameters. */
  def overlaps(col: com.histmersion.util.database.RangeSql.OverlapsCol, p: Period): Sql =
    Sql.of(overlaps(col, "?::bigint", "?::bigint"), Param.L(p.start), Param.L(p.end))

  /** The `int8range` literal of optional inclusive bounds — `[-1200,-999)`, `[-1200,)`, `(,-999)`, and `(,)` (every
    * year) when neither is given. Bind it as `${lit}::int8range`.
    */
  def bounds(start: Option[com.histmersion.util.database.RangeSql.BoundsStart], end: Option[com.histmersion.util.database.RangeSql.BoundsEnd]): com.histmersion.util.database.RangeSql.BoundsResult =
    s"${start.fold("(")(v => s"[$v")},${end.fold(")")(v => s"${v + 1})")}"

  /** [[bounds]] of an optional closed period: `(,)` when there is none — a place, name or relation without a dating
    * holds at every year.
    */
  def always(p: Option[Period]): com.histmersion.util.database.RangeSql.AlwaysResult = bounds(p.map(_.start), p.map(_.end))

  /** [[bounds]], or `None` (SQL NULL: no period) when neither bound is given. */
  def literal(start: Option[com.histmersion.util.database.RangeSql.LiteralStart], end: Option[com.histmersion.util.database.RangeSql.LiteralEnd]): Option[com.histmersion.util.database.RangeSql.LiteralResult] =
    Option.when(start.isDefined || end.isDefined)(bounds(start, end))

  /** [[literal]] of an optional closed period. */
  def literal(p: Option[Period]): Option[com.histmersion.util.database.RangeSql.LiteralResult] = p.flatMap(x => literal(Some(x.start), Some(x.end)))

  /** The model's period (`values.Period`) as the same literal. */
  def literalOf(p: Option[com.histmersion.Period]): Option[com.histmersion.util.database.RangeSql.LiteralOfResult] =
    p.flatMap(x => literal(Some(x.start.value.toLong), Some(x.end.value.toLong)))

  /** A closed [[Period]] from two inclusive bounds read back with [[cols]]: `None` unless both are known. */
  def period(start: Option[com.histmersion.util.database.RangeSql.PeriodStart], end: Option[com.histmersion.util.database.RangeSql.PeriodEnd]): Option[Period] =
    for a <- start; b <- end yield Period(a, b)

  /** A patch's bounds merged with the stored ones: a bound the patch names replaces the stored one, the other stays as
    * it is (#1798: a goal patch that named one bound was silently ignored). `None` when the patch names neither
    * (nothing to write); `Left` when the result would start after it ends (Postgres refuses an inverted range).
    */
  def merge(
      stored: (Option[com.histmersion.util.database.RangeSql.MergeStoredPart1], Option[com.histmersion.util.database.RangeSql.MergeStoredPart2]),
      start: Option[com.histmersion.util.database.RangeSql.MergeStart],
      end: Option[com.histmersion.util.database.RangeSql.MergeEnd],
      what: com.histmersion.util.database.RangeSql.MergeWhat
  ): Option[Either[Api.Error, (Option[com.histmersion.util.database.RangeSql.MergeResultPart2Part1], Option[com.histmersion.util.database.RangeSql.MergeResultPart2Part2])]] =
    Option.when(start.isDefined || end.isDefined) {
      val (s, e) = (start.orElse(stored._1), end.orElse(stored._2))
      (s, e) match
        case (Some(a), Some(b)) if a > b =>
          Left(
            Api.Error(
              s"$what: the period would start (${YearFmt(a)}) after it ends (${YearFmt(b)})",
              Some(Write.CodeInvalid)
            )
          )
        case _ => Right((s, e))
    }
