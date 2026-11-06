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

  type InvalidMsg = InvalidMsg.T
  object InvalidMsg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanDefaults = PlanDefaults.T
  object PlanDefaults extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlanExisting = PlanExisting.T
  object PlanExisting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatementResult = StatementResult.T
  object StatementResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatementTable = StatementTable.T
  object StatementTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatementAnalyze = StatementAnalyze.T
  object StatementAnalyze extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineResult = TableLineResult.T
  object TableLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineTable = TableLineTable.T
  object TableLineTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineDeadBefore = TableLineDeadBefore.T
  object TableLineDeadBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineDeadAfter = TableLineDeadAfter.T
  object TableLineDeadAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineBytesBefore = TableLineBytesBefore.T
  object TableLineBytesBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineBytesAfter = TableLineBytesAfter.T
  object TableLineBytesAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableLineMs = TableLineMs.T
  object TableLineMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val JobKind = com.histmersion.api.maintenance.MaintenanceJobKinds.Vacuum.kind.value

  /** A table name as Postgres stores an unquoted identifier: lower case letters, digits, `_`. */
  private val TableShape = "^[a-z_][a-z0-9_]{0,62}$".r

  private def invalid(msg: com.histmersion.api.maintenance.Vacuum.InvalidMsg) = Api.Error(msg, Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeInvalid)))

  def plan(in: Vacuum.Request, defaults: List[com.histmersion.api.maintenance.Vacuum.PlanDefaults], existing: Set[com.histmersion.api.maintenance.Vacuum.PlanExisting]): Either[Api.Error, VacuumPlan] =
    val asked = in.tables.flatMap(_.split(",").toList).map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val tables = (if asked.isEmpty then defaults.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
                  else asked).distinct
    val shape = tables.filterNot(TableShape.matches)
    val absent = tables.filterNot(existing.contains)
    if tables.isEmpty then Left(invalid(_root_.com.histmersion.api.maintenance.Vacuum.InvalidMsg("no table to vacuum: name one, or set maintenance.vacuum-tables")))
    else if shape.nonEmpty then Left(invalid(_root_.com.histmersion.api.maintenance.Vacuum.InvalidMsg(s"not a table name: ${shape.mkString(", ")}")))
    else if absent.nonEmpty then Left(invalid(_root_.com.histmersion.api.maintenance.Vacuum.InvalidMsg(s"no such table: ${absent.mkString(", ")}")))
    else Right(VacuumPlan(in.analyze, (tables).map(refactorValue2 => _root_.com.histmersion.api.maintenance.VacuumPlan.Tables(refactorValue2))))

  def statement(table: com.histmersion.api.maintenance.Vacuum.StatementTable, analyze: com.histmersion.api.maintenance.Vacuum.StatementAnalyze): com.histmersion.api.maintenance.Vacuum.StatementResult =
    val q = "\"" + (table).value.replace("\"", "\"\"") + "\""
    if (analyze).value then _root_.com.histmersion.api.maintenance.Vacuum.StatementResult(s"VACUUM (ANALYZE) $q") else _root_.com.histmersion.api.maintenance.Vacuum.StatementResult(s"VACUUM $q")

  def describe(p: VacuumPlan): com.histmersion.api.maintenance.Vacuum.DescribeResult =
    s"${if ((p.analyze) == _root_.com.histmersion.api.maintenance.VacuumPlan.Analyze(true)) then "VACUUM (ANALYZE)" else "VACUUM"} of ${p.tables.mkString(", ")}"

  def tableLine(
      table: com.histmersion.api.maintenance.Vacuum.TableLineTable,
      deadBefore: com.histmersion.api.maintenance.Vacuum.TableLineDeadBefore,
      deadAfter: com.histmersion.api.maintenance.Vacuum.TableLineDeadAfter,
      bytesBefore: com.histmersion.api.maintenance.Vacuum.TableLineBytesBefore,
      bytesAfter: com.histmersion.api.maintenance.Vacuum.TableLineBytesAfter,
      ms: com.histmersion.api.maintenance.Vacuum.TableLineMs
  ): com.histmersion.api.maintenance.Vacuum.TableLineResult =
    s"$table: $deadBefore → $deadAfter dead rows, ${Backup.humanBytes(bytesBefore)} → ${Backup.humanBytes(bytesAfter)}" +
      String.format(java.util.Locale.ROOT, " (%.1f s)", ms / 1000.0)

  /** A re-seed or a gazetteer load leaves the old row versions behind as dead tuples, which autovacuum reaches only
    * slowly on tables that size.
    */
  final case class Request(analyze: Request.Analyze = _root_.com.histmersion.api.maintenance.Vacuum.Request.Analyze(false), tables: List[Request.Tables] = Nil)
  object Request:
    type Analyze = Analyze.T
    object Analyze extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tables = Tables.T
    object Tables extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[Request] = KindlingsSchema
      .derived[Request]
      .schema
      .modify(_.analyze)(_.description("also refresh the planner's statistics (VACUUM (ANALYZE))"))
      .modify(_.tables)(_.description("tables to vacuum (default: config maintenance.vacuum-tables)"))
    given Codec.AsObject[Request] = StrictJson.codec(KindlingsCodecAsObject.derived[Request])
