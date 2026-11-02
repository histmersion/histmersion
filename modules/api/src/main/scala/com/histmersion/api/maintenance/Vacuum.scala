package com.histmersion.api.maintenance

import com.histmersion.api.Write
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.Api
import com.histmersion.api.StrictJson
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
object Vacuum:

  val JobKind = com.histmersion.api.maintenance.MaintenanceJobKinds.Vacuum.kind.value

  /** A table name as Postgres stores an unquoted identifier: lower case letters, digits, `_`. */
  private val TableShape = "^[a-z_][a-z0-9_]{0,62}$".r

  private def invalid(msg: String) = Api.Error(msg, Some(Write.CodeInvalid))

  def plan(in: Vacuum.Request, defaults: List[String], existing: Set[String]): Either[Api.Error, VacuumPlan] =
    val asked = in.tables.flatMap(_.split(",").toList).map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val tables = (if asked.isEmpty then defaults.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
                  else asked).distinct
    val shape = tables.filterNot(TableShape.matches)
    val absent = tables.filterNot(existing.contains)
    if tables.isEmpty then Left(invalid("no table to vacuum: name one, or set maintenance.vacuum-tables"))
    else if shape.nonEmpty then Left(invalid(s"not a table name: ${shape.mkString(", ")}"))
    else if absent.nonEmpty then Left(invalid(s"no such table: ${absent.mkString(", ")}"))
    else Right(VacuumPlan(in.analyze, tables))

  def statement(table: String, analyze: Boolean): String =
    val q = "\"" + table.replace("\"", "\"\"") + "\""
    if analyze then s"VACUUM (ANALYZE) $q" else s"VACUUM $q"

  def describe(p: VacuumPlan): String =
    s"${if p.analyze then "VACUUM (ANALYZE)" else "VACUUM"} of ${p.tables.mkString(", ")}"

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

  /** A re-seed or a gazetteer load leaves the old row versions behind as dead tuples, which autovacuum reaches only
    * slowly on tables that size.
    */
  final case class Request(analyze: Boolean = false, tables: List[String] = Nil)
  object Request:
    given Schema[Request] = KindlingsSchema
      .derived[Request]
      .schema
      .modify(_.analyze)(_.description("also refresh the planner's statistics (VACUUM (ANALYZE))"))
      .modify(_.tables)(_.description("tables to vacuum (default: config maintenance.vacuum-tables)"))
    given Codec.AsObject[Request] = StrictJson.codec(KindlingsCodecAsObject.derived[Request])
