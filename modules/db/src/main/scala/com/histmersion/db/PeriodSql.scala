package com.histmersion.db

import com.histmersion.core.*

/** The ONE spelling of a deep-time `int8range` column in SQL (#1798 item 9): how its inclusive bounds
  * are read, how optional bounds are written, and how a PATCH that names one bound merges with the
  * stored other one.
  *
  * Postgres normalises `[a,b]` to `[a,b+1)`, so the inclusive upper bound read back is `upper − 1`
  * (AGENTS.md — the one-year-too-long trap). A bound may be open: `[a,)` is "from a", `(,b+1)` is
  * "until b"; the open side reads back as NULL. Pure, apart from the SQL text it returns. */
object PeriodSql:

  /** `lower(col), upper(col) - 1` — the inclusive bounds of an `int8range` column, in that order. */
  def cols(col: String): String = s"lower($col), upper($col) - 1"

  /** The `int8range` literal of optional inclusive bounds (`[-1200,-999)`, `[-1200,)`, `(,-999)`), or
    * `None` when neither is given — bind it as `${lit}::int8range`. */
  def literal(start: Option[Long], end: Option[Long]): Option[String] =
    (start, end) match
      case (None, None) => None
      case (s, e)       => Some(s"${s.fold("(")(v => s"[$v")},${e.fold(")")(v => s"${v + 1})")}")

  /** A closed [[Period]] from two inclusive bounds read back with [[cols]]: `None` unless both are known. */
  def period(start: Option[Long], end: Option[Long]): Option[Period] =
    for a <- start; b <- end yield Period(a, b)

  /** A patch's bounds merged with the stored ones: a bound the patch names replaces the stored one,
    * the other stays as it is (#1798: a goal patch that named one bound was silently ignored). `None`
    * when the patch names neither (nothing to write); `Left` when the result would start after it ends
    * (Postgres refuses an inverted range). */
  def merge(stored: (Option[Long], Option[Long]), start: Option[Long], end: Option[Long],
      what: String): Option[Either[ApiError, (Option[Long], Option[Long])]] =
    Option.when(start.isDefined || end.isDefined) {
      val (s, e) = (start.orElse(stored._1), end.orElse(stored._2))
      (s, e) match
        case (Some(a), Some(b)) if a > b =>
          Left(ApiError(s"$what: the period would start (${YearFmt(a)}) after it ends (${YearFmt(b)})",
            Some(Write.CodeInvalid)))
        case _ => Right((s, e))
    }
