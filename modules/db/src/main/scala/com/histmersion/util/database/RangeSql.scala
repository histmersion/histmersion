package com.histmersion.util.database

import com.histmersion.api.{ApiError, Write, YearFmt}
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.domain.*
import com.histmersion.api.catalogue.Period

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
  def start(col: String): String = s"lower($col)"

  /** `upper(col) - 1` — the inclusive end (the stored bound is exclusive); NULL when that side is open. */
  def end(col: String): String = s"upper($col) - 1"

  /** `lower(col), upper(col) - 1` — the inclusive bounds of an `int8range` column, in that order. */
  def cols(col: String): String = s"${start(col)}, ${end(col)}"

  /** The same, each named: `lower(col) AS a, upper(col) - 1 AS b`. */
  def cols(col: String, startAs: String, endAs: String): String = s"${start(col)} AS $startAs, ${end(col)} AS $endAs"

  /** `int8range(lo, hi + 1, '[)')` — the range of two INCLUSIVE bounds given as SQL expressions (columns, `?::bigint`);
    * a NULL bound is an open side.
    */
  def range(lo: String, hi: String): String = s"int8range($lo, $hi + 1, '[)')"

  /** `col && int8range(lo, hi, '[]')` — the column overlaps the inclusive window `[lo, hi]` given as SQL expressions.
    * The Scala twin is `FilterSql.overlaps`.
    */
  def overlaps(col: String, lo: String, hi: String): String = s"$col && int8range($lo, $hi, '[]')"

  /** [[overlaps]] with the window bound as two parameters. */
  def overlaps(col: String, p: Period): Sql =
    Sql.of(overlaps(col, "?::bigint", "?::bigint"), Param.L(p.start), Param.L(p.end))

  /** The `int8range` literal of optional inclusive bounds — `[-1200,-999)`, `[-1200,)`, `(,-999)`, and `(,)` (every
    * year) when neither is given. Bind it as `${lit}::int8range`.
    */
  def bounds(start: Option[Long], end: Option[Long]): String =
    s"${start.fold("(")(v => s"[$v")},${end.fold(")")(v => s"${v + 1})")}"

  /** [[bounds]] of an optional closed period: `(,)` when there is none — a place, name or relation without a dating
    * holds at every year.
    */
  def always(p: Option[Period]): String = bounds(p.map(_.start), p.map(_.end))

  /** [[bounds]], or `None` (SQL NULL: no period) when neither bound is given. */
  def literal(start: Option[Long], end: Option[Long]): Option[String] =
    Option.when(start.isDefined || end.isDefined)(bounds(start, end))

  /** [[literal]] of an optional closed period. */
  def literal(p: Option[Period]): Option[String] = p.flatMap(x => literal(Some(x.start), Some(x.end)))

  /** The model's period (`values.Period`) as the same literal. */
  def literalOf(p: Option[com.histmersion.Period]): Option[String] =
    p.flatMap(x => literal(Some(x.start.value.toLong), Some(x.end.value.toLong)))

  /** A closed [[Period]] from two inclusive bounds read back with [[cols]]: `None` unless both are known. */
  def period(start: Option[Long], end: Option[Long]): Option[Period] =
    for a <- start; b <- end yield Period(a, b)

  /** A patch's bounds merged with the stored ones: a bound the patch names replaces the stored one, the other stays as
    * it is (#1798: a goal patch that named one bound was silently ignored). `None` when the patch names neither
    * (nothing to write); `Left` when the result would start after it ends (Postgres refuses an inverted range).
    */
  def merge(
      stored: (Option[Long], Option[Long]),
      start: Option[Long],
      end: Option[Long],
      what: String
  ): Option[Either[ApiError, (Option[Long], Option[Long])]] =
    Option.when(start.isDefined || end.isDefined) {
      val (s, e) = (start.orElse(stored._1), end.orElse(stored._2))
      (s, e) match
        case (Some(a), Some(b)) if a > b =>
          Left(
            ApiError(
              s"$what: the period would start (${YearFmt(a)}) after it ends (${YearFmt(b)})",
              Some(Write.CodeInvalid)
            )
          )
        case _ => Right((s, e))
    }
