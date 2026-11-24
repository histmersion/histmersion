package com.histmersion
package util.data.backup
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.backup.Backup
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataContributions
import com.histmersion.util.database.Transactor
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput, ProcessResult}
import com.histmersion.RowCount
import com.histmersion.util.storage.{ByteSource, HandedFile, Location, Storage, Store, TempName}

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256
import com.histmersion.lint.{LintRule, tolerated}

/** The database's dumps: `pg_dump` / `pg_restore` against the database this server is configured for (the CLI holds no
  * credentials, so the dump is taken here), the archives in the backups store (`backups.dir`), their listing and
  * housekeeping (`backups.keep`), and what a restore must not drop unasked — the registry's guarded tables. Archives
  * are custom-format (`-Fc`: compressed, selective-restore capable).
  */
trait BackupService:

  /** A dump into `histmersion-<UTC timestamp>.dump`, named but not started. */
  def prepare(now: Instant): Either[Api.Error, PreparedBackup]

  /** A dump, run to its end. */
  def create(now: Instant): Either[Api.Error, Backup.Result] = prepare(now).flatMap(_.run())

  /** The archives, newest first by when they were taken ([[Backup.newestFirst]]). */
  def list: Either[Api.Error, List[Backup.Info]]

  /** The ONLY way a client-supplied name names an archive: syntactically safe AND present in the listing. A name that
    * is neither cannot escape the store.
    */
  def resolve(file: PreparedBackup.File): Either[Api.Error, Store.Key]

  /** The archive `file` itself, for the download (`GET /api/backups/{file}`). */
  def archive(file: PreparedBackup.File): Either[Api.Error, HandedFile]

  /** `pg_restore --clean --if-exists` of `archive`: how long it took, and what `pg_restore` complained of. */
  def restore(archive: Store.Key): Either[Api.Error, (BackupService.RestoreDuration, List[BackupService.RestoreNote])]

  /** The rows the guarded tables hold: a restore over any of them needs `force`. */
  def guardedRows: RowCount

  /** The refusal of a restore over `rows` guarded rows. */
  def refusal(rows: RowCount): Api.Error

object BackupService:
  type RestoreDuration = RestoreDuration.T
  object RestoreDuration extends Newtype[Long, Pure]
  type RestoreNote = RestoreNote.T
  object RestoreNote extends Newtype[String, Pure]

  /** Over the backups store of `storage`, the client tools found in `backups.pg-bin-dir` (its store) or else on
    * `searchPath`, launched through `processes`; the guarded tables of `registry` counted through `transactor`.
    * `settings` is read at each use (a config reload switches it).
    */

  def apply(
      settings: () => AppConfig,
      storage: Storage,
      processes: Processes,
      searchPath: () => List[Store],
      transactor: Transactor,
      registry: DataContributions
  ): BackupService = Implementation(settings, storage, processes, searchPath, transactor, registry)

  private val CreatedAt = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

  /** The pending suffix of a dump being written: the listing shows only finished archives. */
  private val Pending = ".part"

  private def guards(registry: DataContributions): List[GuardedTable] = registry.backups.flatMap(_._2.guarded)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rowsLeftOut(registry: DataContributions): List[String] =
    registry.backups.flatMap(_._2.rowsLeftOut).map(t => s"--exclude-table-data=public.$t")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def problem(message: String): Api.Error = Api.Error(Problem.Unclassified(Legacy.Text(message)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def command(parts: String*): List[ProcessLaunch.Command] = parts.toList.map(ProcessLaunch.Command(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def refusalOver(registry: DataContributions, rows: RowCount): Api.Error =
    val noun = guards(registry).map(_.noun).distinct.mkString("/")
    problem(s"the database still holds $rows $noun(s); restoring would drop them — re-run with force")

  /** The archives of `store`, newest first; `where` names it in the error. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def listing(store: Store): Either[Api.Error, List[Backup.Info]] =
    if !store.isDirectory(Store.Key.Root) then Right(Nil)
    else
      scala.util
        .Try(
          Backup.newestFirst(
            store
              .names()
              .map(n => Store.Key.of(n.value))
              .collect { case Right(k) if store.isFile(k) => k }
              .flatMap(k =>
                Backup.safeName(Backup.Name(k.name.value)).toOption.flatMap(n => store.stat(k).map(s => (n, s)))
              )
              .map((n, s) =>
                Backup.Info(
                  n,
                  ByteCount(s.size.value),
                  Backup.Info.CreatedAt(CreatedAt.format(Backup.takenAt(n, s.modified)))
                )
              )
          )
        )
        .toEither
        .left
        .map(e => problem(s"cannot list ${store.where().value}: ${e.getMessage}"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def resolving(store: Store, file: PreparedBackup.File): Either[Api.Error, Store.Key] =
    for
      name <- Backup.safeName(Backup.Name(file.value)).left.map(m => problem(m.sentence))
      known <- listing(store)
      _ <- if known.exists(_.file == name) then Right(()) else Left(Backup.missing(name))
      key <- Store.Key.either(name.value).left.map(m => problem(m))
    yield key

  /** The name of a new archive taken at `now`, not taken by an archive nor by a dump being written. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def freshName(store: Store, now: Instant): PreparedBackup.File =
    PreparedBackup.File(
      Backup
        .uniqueName(
          Backup.Name(Backup.fileName(now).value),
          n => store.exists(Store.Key.applyUnsafe(n.value)) || store.exists(Store.Key.applyUnsafe(n.value + Pending))
        )
        .value
    )

  /** Delete the archives beyond `keep` — AFTER a dump succeeded, never the one just written. Best effort: a file that
    * will not go is logged and stays; the backup itself still succeeded.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def pruned(store: Store, keep: Backup.Retention, keeping: PreparedBackup.File): List[Backup.Result.Pruned] =
    listing(store).toOption.toList.flatMap { rows =>
      Backup
        .toPrune(rows.map(_.file), keep)
        .filterNot(_.value == keeping.value)
        .filter { n =>
          scala.util
            .Try(store.delete(Store.Key.applyUnsafe(n.value)))
            .fold(e => { scribe.warn(s"backup: could not prune ${n.value}: ${e.getMessage}"); false }, identity)
        }
        .map(n => Backup.Result.Pruned(n.value))
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def usable(store: Store): Either[Api.Error, Store] =
    store
      .makeDirectory()
      .left
      .map(e => problem(s"backups directory ${store.where().value} is not usable: ${e.message.sentence}"))
      .map(_ => store)

  final private class Implementation(
      settings: () => AppConfig,
      storage: Storage,
      processes: Processes,
      searchPath: () => List[Store],
      transactor: Transactor,
      registry: DataContributions
  ) extends BackupService:

    private def backups = settings().backups
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def timeoutS = math.max(1, backups.timeoutSeconds.value: Int)
    private def store: Store = storage.store(Location.Backups)

    /** `backups.pg-bin-dir` wins when set (homebrew's libpq is keg-only, so `pg_dump` is off PATH on a macOS dev box);
      * otherwise the search path. The failure message says exactly what to do.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def exe(name: String): Either[Api.Error, ProcessLaunch.Command] =
      val key = Store.Key.applyUnsafe(name)
      Option(backups.pgBinDir.value)
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap(_ => storage.find(Location.PostgresTools)) match
        case Some(tools) if tools.isExecutable(key) => Right(ProcessLaunch.Command(tools.where(key).value))
        case Some(tools)                            =>
          Left(
            problem(
              s"no executable $name at ${tools.where(key)} (histmersion.backups.pg-bin-dir / HISTMERSION_PG_BIN_DIR)"
            )
          )
        case None =>
          searchPath()
            .find(_.isExecutable(key))
            .map(s => ProcessLaunch.Command(s.where(key).value))
            .toRight(
              problem(
                s"$name is not on PATH — install the PostgreSQL client tools, or point " +
                  s"HISTMERSION_PG_BIN_DIR at the directory holding them"
              )
            )

    /** Run a command with `PGPASSWORD` set, its merged output going to a log file in a scratch store. Returns (exit
      * code, the output's last lines).
      *
      * The output goes to a FILE, not a pipe. Reading a pipe to EOF and *then* calling `waitFor(timeout)` is not a
      * timeout at all: the read only ends when the process closes its stdout, i.e. when it exits — so a hung `pg_dump`
      * hung the request forever. Redirecting to a file lets the process write freely (no pipe-buffer deadlock either)
      * while `waitFor` does the actual bounding, and the output is read back afterwards.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def run(cmd: List[ProcessLaunch.Command]): Either[Api.Error, (ProcessResult.ExitCode, List[String])] =
      val scratch = storage.scratch(Storage.ScratchPurpose("histmersion-pg-"))
      val log = Store.Key.applyUnsafe("pg.log")
      val cfg = settings()
      val launch = ProcessLaunch(
        cmd,
        environment = Map(
          ProcessLaunch.EnvironmentKey("PGPASSWORD") -> ProcessLaunch.EnvironmentValue(cfg.db.password.value),
          ProcessLaunch.EnvironmentKey("PGUSER") -> ProcessLaunch.EnvironmentValue(cfg.db.user.value)
        ),
        output = ProcessOutput.WriteTo(scratch.store, log)
      )
      try
        scala.util
          .Try {
            val p = processes.start(launch).fold(e => throw java.io.IOException(e.sentence), identity)
            val ended = p.waitFor(Processes.WaitTimeout(timeoutS.toLong), TimeUnit.SECONDS)
            if !ended then
              p.destroyForcibly()
              val _ = p.waitFor(Processes.WaitTimeout(5L), TimeUnit.SECONDS) // let the kill land before reading the log
              (ProcessResult.ExitCode(Int.MinValue), tail(scratch.store, log))
            else (p.waitFor(), tail(scratch.store, log))
          }
          .toEither
          .left
          .map(e =>
            problem(
              s"could not run ${cmd.headOption.fold("")(_.value)}: ${e.getClass.getSimpleName}: ${e.getMessage}"
            )
          )
          .flatMap {
            case (code, _) if code.value == Int.MinValue =>
              Left(problem(s"${cmd.headOption.fold("")(_.value)} timed out after ${timeoutS}s"))
            case ok => Right(ok)
          }
      finally scratch.close()

    /** The captured output, bounded: a `pg_restore` complaining about every statement can write megabytes, and only the
      * last handful of lines ever reaches an error message.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def tail(logs: Store, log: Store.Key): List[String] =
      scala.util
        .Try(logs.get(log).fold(List.empty[String])(b => (b.text().value: String).linesIterator.toList.takeRight(200)))
        .getOrElse(Nil)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def target: Either[Api.Error, Backup.PgTarget] =
      Backup.parseJdbc(Backup.JdbcUrl(settings().db.url.value)).left.map(m => problem(s"cannot back up: ${m.sentence}"))

    /** A non-zero exit becomes an `ApiError` quoting the last output lines — the actual `pg_*` message, not a stack
      * trace.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def succeeded(what: String, res: (ProcessResult.ExitCode, List[String])): Either[Api.Error, Unit] =
      if res._1.value == 0 then Right(())
      else Left(problem(s"$what failed (exit ${res._1.value}): ${res._2.takeRight(5).mkString("; ")}"))

    /** `pg_dump -Fc` into the archive's reservation (`<name>.part`), moved into place only on success, so the listing
      * never shows a half-written archive.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def prepare(now: Instant): Either[Api.Error, PreparedBackup] =
      for
        t <- target
        pgDump <- exe("pg_dump")
        dir <- usable(store)
        name = freshName(dir, now)
      yield PreparedBackup(
        name,
        () =>
          val key = Store.Key.applyUnsafe(name.value)
          val started = System.nanoTime()
          for
            reservation <- dir
              .reserve(key, TempName.Suffixed(TempName.Suffixed.Suffix(Pending)))
              .left
              .map(e => problem(e.message.sentence))
            stored <-
              try
                for
                  part <- reservation.onDisk.left.map(e => problem(e.message.sentence))
                  res <- run(
                    List(pgDump) ++ command(t.args*) ++ command(rowsLeftOut(registry)*) ++
                      command("-Fc", "-f", part.toString)
                  )
                  _ <- succeeded("pg_dump", res)
                  done <- reservation
                    .commit()
                    .left
                    .map(e => problem(s"could not finish ${dir.where(key).value}: ${e.message.sentence}"))
                yield done
              finally reservation.close()
          yield Backup.Result(
            Backup.Result.File(name.value),
            ByteCount(stored.size.value),
            Sha256(dir.get(key).fold("")(_.sha256().value)),
            Backup.Result.DurationMs((System.nanoTime() - started) / 1000000L),
            pruned(dir, Backup.Retention(backups.keep.value), keeping = name)
          )
      )

    def list: Either[Api.Error, List[Backup.Info]] = listing(store)

    def resolve(file: PreparedBackup.File): Either[Api.Error, Store.Key] = resolving(store, file)

    def archive(file: PreparedBackup.File): Either[Api.Error, HandedFile] =
      val dir = store
      resolving(dir, file).flatMap(k => dir.onDiskFile(k).left.map(e => problem(e.message.sentence)))

    /** `pg_restore --clean --if-exists`. NOTE: `pg_restore` exits 0 even when it skipped statements the server did not
      * understand (a dump taken by a newer `pg_dump` emits `SET transaction_timeout`, which Postgres < 17 rejects), so
      * its complaints are returned as warnings rather than swallowed.
      *
      * `--no-owner --no-privileges`: everything restored belongs to the role doing the restore, and the dump's GRANTs
      * are skipped, so a dump taken on an installation with other role names loads cleanly. The caller re-issues the
      * read-only role's grants afterwards.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def restore(archive: Store.Key): Either[Api.Error, (RestoreDuration, List[RestoreNote])] =
      for
        t <- target
        pgRestore <- exe("pg_restore")
        file <- store.onDisk(archive).left.map(e => problem(e.message.sentence))
        started = System.nanoTime()
        res <- run(
          List(pgRestore) ++ command(t.args*) ++
            command("--clean", "--if-exists", "--no-owner", "--no-privileges", file.toString)
        )
        warnings <- Backup
          .restoreOutcome(Problem.Io.ExitCode(res._1.value), res._2.map(Backup.ToolOutput(_)))
          .left
          .map(m => problem(m.sentence))
      yield (RestoreDuration((System.nanoTime() - started) / 1000000L), warnings.map(o => RestoreNote(o.value)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def guardedRows: RowCount =
      transactor.read(RowCount(guards(registry).map(g => BackupRepo.rows(g.table).value: Int).sum))

    def refusal(rows: RowCount): Api.Error = refusalOver(registry, rows)

  /** Dumps that are files of `store` holding `dumped` (no `pg_dump` runs), restores that change nothing, and guarded
    * rows that are `rows` (a test sets them). It lists, names, prunes and refuses as the database implementation does.
    */
  final class InMemory(
      store: Store,
      keep: InMemory.Keep,
      registry: DataContributions,
      rows: AtomicReference[RowCount] = AtomicReference(RowCount(0)),
      dumped: Array[Byte] = "PGDMP".getBytes("UTF-8")
  ) extends BackupService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def prepare(now: Instant): Either[Api.Error, PreparedBackup] =
      usable(store).map { dir =>
        val name = freshName(dir, now)
        PreparedBackup(
          name,
          () =>
            val key = Store.Key.applyUnsafe(name.value)
            dir
              .put(key, ByteSource.Content(dumped))
              .left
              .map(e => problem(s"could not finish ${dir.where(key).value}: ${e.message.sentence}"))
              .map(s =>
                Backup.Result(
                  Backup.Result.File(name.value),
                  ByteCount(s.size.value),
                  Sha256(dir.get(key).fold("")(_.sha256().value)),
                  Backup.Result.DurationMs(0L),
                  pruned(dir, Backup.Retention(keep.value), name)
                )
              )
        )
      }
    def list: Either[Api.Error, List[Backup.Info]] = listing(store)
    def resolve(file: PreparedBackup.File): Either[Api.Error, Store.Key] = resolving(store, file)
    def archive(file: PreparedBackup.File): Either[Api.Error, HandedFile] =
      resolving(store, file).flatMap(k => store.onDiskFile(k).left.map(e => problem(e.message.sentence)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def restore(archive: Store.Key): Either[Api.Error, (RestoreDuration, List[RestoreNote])] =
      store.get(archive).toRight(Backup.missing(Backup.Result.File(archive.value))).map(_ => (RestoreDuration(0L), Nil))
    def guardedRows: RowCount = rows.get
    def refusal(n: RowCount): Api.Error = refusalOver(registry, n)

  object InMemory:
    type Keep = Keep.T
    object Keep extends Newtype[Int, Pure]
