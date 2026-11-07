package com.histmersion.server.instance

import com.histmersion.api.Write
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.library.search.QueryAccess
import com.histmersion.server.Deps
import com.histmersion.util.data.backup.{BackupJobs, BackupService}
import com.histmersion.util.data.maintenance.{CacheEvent, CacheName, MaintenanceSite, MaintenanceSlice}
import com.histmersion.util.data.maintenance.VacuumJobs

import java.time.Instant
import com.histmersion.api.backup.{Backup, RestoreRequest, RestoreResult}
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.api.Api
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.util.data.maintenance.MaintenanceTask

/** This server's upkeep as REST, MCP and the CLI reach it: the backups (a `pg_dump` as a job, its status, the listing
  * and an archive), the restore under the maintenance lock, the vacuum and the reindex the registry runs, and why a
  * scheduled backup or repair must not start now. `pauseIngest` and `resumeIngest` hold the OCR workers off during a
  * restore; `restoredCaches` clears what this server holds of each registered cache a restore makes stale.
  */
final class MaintenanceControllers(
    deps: Deps,
    backups: BackupService,
    pauseIngest: () => Unit,
    resumeIngest: () => Unit,
    restoredCaches: Map[CacheName, () => Unit]
):
  private val ds = deps.ds
  private def cfg = deps.cfg
  private val maintenance = deps.maintenance
  private val backupJobs = BackupJobs(backups, deps.cfg.backups.requestWaitSeconds, deps.pins)
  private val vacuumJobs = VacuumJobs(ds, deps.maintenance, Deps.data, () => cfg.maintenance.tables, deps.pins)

  /** `pg_dump -Fc` of the server's own database into `backups.dir`, as a job (see [[BackupJobs]]): the answer is the
    * finished backup, or `running` when it outlasts the request.
    */
  def backup(): Either[Api.Error, Backup.Status] =
    maintenance.guard("backup")(backupJobs.start(Instant.now(deps.clock)))

  /** `POST /api/maintenance/vacuum` (#1445) — VACUUM [(ANALYZE)] of the named tables (default
    * `maintenance.vacuum-tables`) as a job (see [[VacuumJobs]]).
    */
  def vacuum(in: Vacuum.Request): Either[Api.Error, JobInfo] = vacuumJobs.start(in)

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[com.histmersion.server.instance.MaintenanceControllers.Cursor], limit: Option[com.histmersion.PageSize]): Either[Api.Error, ReindexResult] =
    maintenance.guard("reindex"):
      Deps.data
        .task(MaintenanceTask.Name("reindex"))
        .toRight(Api.Error("no reindex is registered"))
        .map(
          _.run(MaintenanceSlice(after.map(_.trim).filter(_.nonEmpty), limit.filter(_ > 0)), MaintenanceSite(ds, cfg))
        )

  /** Why a scheduled backup or repair must not start now (the autopilot's housekeeping): a restore holds the
    * maintenance lock, or a backup or a vacuum is still running. pg_dump reads one consistent snapshot, so ingests, OCR
    * and agent runs going on beside it are fine.
    */
  def housekeepingBlock: Option[com.histmersion.server.instance.MaintenanceControllers.Reason] =
    if maintenance.active then Some("a restore holds the maintenance lock")
    else if backupJobs.running then Some("a backup is still running")
    else if vacuumJobs.isRunning then Some("a vacuum is still running")
    else None

  def backupStatus: Either[Api.Error, Backup.Status] =
    backupJobs.status.toRight(Api.Error("no backup has run since the server started", Some(Write.CodeNotFound)))

  def backupList: Either[Api.Error, List[Backup.Info]] = backups.list

  /** The archive itself, for `GET /api/backups/{file}` — validated against the listing first. */
  def backupFile(file: com.histmersion.server.instance.MaintenanceControllers.BackupFileName): Either[Api.Error, java.io.File] = backups.archive(file)

  /** Restore under the maintenance lock: every other write is answered 503 until it returns. Refuses a database that
    * still holds sources unless `force` (`pg_restore --clean` DROPS them), and re-validates the schema with Flyway
    * afterwards so a stale dump cannot pass silently.
    */
  def restore(req: RestoreRequest): Either[Api.Error, RestoreResult] =
    if backupJobs.running then
      Left(
        Api.Error("a backup is still running — wait for `histmersion backup` to finish", Some(Backup.CodeMaintenance))
      )
    else if vacuumJobs.isRunning then
      Left(
        Api.Error(
          "a vacuum is still running — wait for its job (`histmersion jobs`) to finish",
          Some(Backup.CodeMaintenance)
        )
      )
    else
      maintenance.exclusively("restore"):
        // The OCR workers hold long transactions and write sources; a job landing mid-restore writes
        // into a schema that is being replaced. Paused for the duration, resumed whatever happens.
        pauseIngest()
        try restoreNow(req)
        finally resumeIngest()

  private def restoreNow(req: RestoreRequest): Either[Api.Error, RestoreResult] =
    val before = backups.guardedRows
    for
      archive <- backups.resolve(req.file)
      _ <- if req.force || before == 0 then Right(()) else Left(backups.refusal(before))
      r <- backups.restore(archive)
      // Every pooled connection now holds a session cache describing types the restore dropped;
      // PostGIS in particular fails outright on the next spatial query. Throw them away here,
      // while the maintenance lock still keeps everyone else out.
      _ = { Db.evict(ds); Db.evict(deps.queryDs) }
      // An older dump comes up to this build's schema now rather than at the next restart; the
      // database settings and the read-only role's grants are not in a `--no-privileges` dump.
      _ <- scala.util
        .Try(Migrate.run(ds))
        .toEither
        .left
        .map(e => Api.Error(s"restored ${req.file}, but migrating it failed: ${e.getMessage}"))
      _ = Db.ensureDatabaseSettings(ds)
      role = QueryAccess
        .provision(ds, cfg.query)
        .left
        .toOption
        .map(m => s"the read-only role '${cfg.query.user}' was not re-provisioned: $m")
        .toList
      _ = { Db.evict(ds); Db.evict(deps.queryDs) }
      // the restored versions of what the map's tiles read may be ones the kept tiles were cut under
      _ = clearOnRestore()
      v <- Migrate.validate(ds).left.map(m => Api.Error(s"restored ${req.file}, but the schema does not validate: $m"))
    yield RestoreResult(req.file, v, before, backups.guardedRows, r._1, r._2 ++ role)

  /** The registered caches a restore makes stale, cleared: what this server holds of each. */
  private def clearOnRestore(): Unit =
    Deps.data.caches
      .filter(_.staleAfter.contains(CacheEvent.Restored))
      .foreach(c => restoredCaches.get(c.name).foreach(_()))

object MaintenanceControllers:
  type BackupFileName = BackupFileName.T
  object BackupFileName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cursor = Cursor.T
  object Cursor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
