package com.histmersion.api

object Vacuum:

  /** The `jobs.kind` of a vacuum job. */
  val JobKind = com.histmersion.api.JobKind.Vacuum.wire

  /** A table name as Postgres stores an unquoted identifier: lower case letters, digits, `_`. */
  private val TableShape = "^[a-z_][a-z0-9_]{0,62}$".r

  private def invalid(msg: String) = ApiError(msg, Some(Write.CodeInvalid))

  /** The tables a request names (trimmed, lower-cased, de-duplicated, in order), or `defaults` when it names none —
    * each a table of the store (`existing`). Pure.
    */
  def plan(in: VacuumRequest, defaults: List[String], existing: Set[String]): Either[ApiError, VacuumPlan] =
    val asked = in.tables.flatMap(_.split(",").toList).map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val tables = (if asked.isEmpty then defaults.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
                  else asked).distinct
    val shape = tables.filterNot(TableShape.matches)
    val absent = tables.filterNot(existing.contains)
    if tables.isEmpty then Left(invalid("no table to vacuum: name one, or set maintenance.vacuum-tables"))
    else if shape.nonEmpty then Left(invalid(s"not a table name: ${shape.mkString(", ")}"))
    else if absent.nonEmpty then Left(invalid(s"no such table: ${absent.mkString(", ")}"))
    else Right(VacuumPlan(in.analyze, tables))

  /** The statement for one table, its name quoted (it passed [[plan]]'s shape check as well). Pure. */
  def statement(table: String, analyze: Boolean): String =
    val q = "\"" + table.replace("\"", "\"\"") + "\""
    if analyze then s"VACUUM (ANALYZE) $q" else s"VACUUM $q"

  /** `VACUUM (ANALYZE) of places, place_names` — for a person (the jobs list). Pure. */
  def describe(p: VacuumPlan): String =
    s"${if p.analyze then "VACUUM (ANALYZE)" else "VACUUM"} of ${p.tables.mkString(", ")}"

  /** One table's line in the job's summary: dead row versions and size before → after. Pure. */
  def tableLine(
      table: String,
      deadBefore: Long,
      deadAfter: Long,
      bytesBefore: Long,
      bytesAfter: Long,
      ms: Long
  ): String =
    s"$table: $deadBefore → $deadAfter dead rows, ${Backup.humanBytes(bytesBefore)} → ${Backup.humanBytes(bytesAfter)}" +
      String.format(java.util.Locale.ROOT, " (%.1f s)", ms / 1000.0)
