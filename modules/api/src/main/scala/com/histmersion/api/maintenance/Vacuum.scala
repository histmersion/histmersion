package com.histmersion
package api.maintenance
import com.histmersion.util.problem.Problem

import com.histmersion.api.Write
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import com.histmersion.api.StrictJson
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.lint.{tolerated, LintRule}
object Vacuum:
  type DeadRows = DeadRows.T
  object DeadRows extends Newtype[Long, Pure]
  type Table = Table.T
  object Table extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val JobKind = MaintenanceJobKinds.Vacuum.kind.value

  /** A table name as Postgres stores an unquoted identifier: lower case letters, digits, `_`. */
  private val TableShape = "^[a-z_][a-z0-9_]{0,62}$".r

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def plan(in: Request, defaults: List[Table], existing: Set[Table]): Either[Api.Error, VacuumPlan] =
    val asked =
      in.tables.flatMap(_.value.split(",").toList).map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val tables =
      (if asked.isEmpty then defaults.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
       else asked).distinct
    val shape = tables.filterNot(TableShape.matches)
    val absent = tables.filterNot(existing.map(_.value).contains)
    if tables.isEmpty then Left(Write.invalid(Problem.Invalid.Missing.here(Problem.Field("tables"))))
    else if shape.nonEmpty then
      Left(
        Write.invalid(
          Problem.Invalid.Unreadable
            .here(Problem.TypeName.of(Table), Problem.Raw(s"${shape.mkString(", ")}"), Vector(Problem.Raw("sources")))
        )
      )
    else if absent.nonEmpty then
      Left(Write.invalid(Problem.NotFound.Entity(Problem.TypeName.of(Table), Problem.Raw(s"${absent.mkString(", ")}"))))
    else Right(VacuumPlan(in.analyze, (tables).map(refactorValue2 => Request.Tables(refactorValue2))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statement(table: Table, analyze: Request.Analyze): String =
    val q = "\"" + (table).value.replace("\"", "\"\"") + "\""
    if (analyze).value then (s"VACUUM (ANALYZE) $q") else (s"VACUUM $q")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def describe(p: VacuumPlan): String =
    (s"${if (p.analyze) == Request.Analyze(true) then "VACUUM (ANALYZE)" else "VACUUM"} of ${p.tables.mkString(", ")}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tableLine(
      table: Table,
      deadBefore: DeadRows,
      deadAfter: DeadRows,
      bytesBefore: ByteCount,
      bytesAfter: ByteCount,
      ms: Storage.Report.DurationMillis
  ): String =
    s"$table: $deadBefore → $deadAfter dead rows, ${Backup.humanBytes(bytesBefore)} → ${Backup.humanBytes(bytesAfter)}" +
      String.format(java.util.Locale.ROOT, " (%.1f s)", ms.value / 1000.0)

  /** A re-seed or a gazetteer load leaves the old row versions behind as dead tuples, which autovacuum reaches only
    * slowly on tables that size.
    */
  final case class Request(analyze: Request.Analyze = Request.Analyze(false), tables: List[Request.Tables] = Nil)
      derives StrictJson
  object Request:
    type Analyze = Analyze.T
    object Analyze extends Newtype[Boolean, Pure]
    type Tables = Tables.T
    object Tables extends Newtype[String, Pure]

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[Request] = KindlingsSchema
      .derived[Request]
      .schema
      .modify(_.analyze)(_.description("also refresh the planner's statistics (VACUUM (ANALYZE))"))
      .modify(_.tables)(_.description("tables to vacuum (default: config maintenance.vacuum-tables)"))
