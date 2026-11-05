package com.histmersion.util.data.backup

import com.histmersion.api.backup.Backup
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataContributions
import com.histmersion.util.database.Transactor
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput}
import com.histmersion.util.storage.{HandedFile, Location, Storage, Store, TempName}

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.Api

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
  def resolve(file: com.histmersion.util.data.backup.BackupService.ResolveFile): Either[Api.Error, Store.Key]

  /** The archive `file` itself, for the download (`GET /api/backups/{file}`). */
  def archive(file: com.histmersion.util.data.backup.BackupService.ArchiveFile): Either[Api.Error, HandedFile]

  /** `pg_restore --clean --if-exists` of `archive`: how long it took, and what `pg_restore` complained of. */
  def restore(archive: Store.Key): Either[Api.Error, (com.histmersion.util.data.backup.BackupService.RestoreResultPart2Part1, List[com.histmersion.util.data.backup.BackupService.RestoreResultPart2Part2])]

  /** The rows the guarded tables hold: a restore over any of them needs `force`. */
  def guardedRows: com.histmersion.util.data.backup.BackupService.GuardedRowsResult

  /** The refusal of a restore over `rows` guarded rows. */
  def refusal(rows: com.histmersion.util.data.backup.BackupService.RefusalRows): Api.Error

object BackupService:

  /** Over the backups store of `storage`, the client tools found in `backups.pg-bin-dir` (its store) or else on
    * `searchPath`, launched through `processes`; the guarded tables of `registry` counted through `transactor`.
    * `settings` is read at each use (a config reload switches it).
    */
  type ResolveFile = ResolveFile.T
  object ResolveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveFile = ArchiveFile.T
  object ArchiveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreResultPart2Part1 = RestoreResultPart2Part1.T
  object RestoreResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreResultPart2Part2 = RestoreResultPart2Part2.T
  object RestoreResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedRowsResult = GuardedRowsResult.T
  object GuardedRowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalRows = RefusalRows.T
  object RefusalRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalOverRows = RefusalOverRows.T
  object RefusalOverRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolvingFile = ResolvingFile.T
  object ResolvingFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FreshNameResult = FreshNameResult.T
  object FreshNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrunedResult = PrunedResult.T
  object PrunedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrunedKeep = PrunedKeep.T
  object PrunedKeep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PrunedKeeping = PrunedKeeping.T
  object PrunedKeeping extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  private def refusalOver(registry: DataContributions, rows: com.histmersion.util.data.backup.BackupService.RefusalOverRows): Api.Error =
    val noun = guards(registry).map(_.noun).distinct.mkString("/")
    Api.Error(s"the database still holds $rows $noun(s); restoring would drop them — re-run with force")

  /** The archives of `store`, newest first; `where` names it in the error. */
  private def listing(store: Store): Either[Api.Error, List[Backup.Info]] =
    if !store.isDirectory(Store.Key.Root) then Right(Nil)
    else
      scala.util
        .Try(
          Backup.newestFirst(
            store
              .names()
              .map(Store.Key.of(_))
              .collect { case Right(k) if store.isFile(k) => k }
              .flatMap(k => Backup.safeName(k.name).toOption.flatMap(n => store.stat(k).map(s => (n, s))))
              .map((n, s) => Backup.Info(n, s.size.value, CreatedAt.format(Backup.takenAt(n, s.modified))))
          )
        )
        .toEither
        .left
        .map(e => Api.Error(s"cannot list ${store.where()}: ${e.getMessage}"))

  private def resolving(store: Store, file: com.histmersion.util.data.backup.BackupService.ResolvingFile): Either[Api.Error, Store.Key] =
    for
      name <- Backup.safeName(file).left.map(m => Api.Error(m))
      known <- listing(store)
      _ <- if known.exists(_.file == name) then Right(()) else Left(Backup.missing(name))
      key <- Store.Key.either(name).left.map(m => Api.Error(m))
    yield key

  /** The name of a new archive taken at `now`, not taken by an archive nor by a dump being written. */
  private def freshName(store: Store, now: Instant): com.histmersion.util.data.backup.BackupService.FreshNameResult =
    Backup.uniqueName(
      Backup.fileName(now),
      n => store.exists(Store.Key.applyUnsafe(n)) || store.exists(Store.Key.applyUnsafe(n + Pending))
    )

  /** Delete the archives beyond `keep` — AFTER a dump succeeded, never the one just written. Best effort: a file that
    * will not go is logged and stays; the backup itself still succeeded.
    */
  private def pruned(store: Store, keep: com.histmersion.util.data.backup.BackupService.PrunedKeep, keeping: com.histmersion.util.data.backup.BackupService.PrunedKeeping): List[com.histmersion.util.data.backup.BackupService.PrunedResult] =
    listing(store).toOption.toList.flatMap { rows =>
      Backup.toPrune(rows.map(_.file), keep).filterNot(_ == keeping).filter { n =>
        scala.util
          .Try(store.delete(Store.Key.applyUnsafe(n)))
          .fold(e => { scribe.warn(s"backup: could not prune $n: ${e.getMessage}"); false }, identity)
      }
    }

  private def usable(store: Store): Either[Api.Error, Store] =
    store
      .makeDirectory()
      .left
      .map(e => Api.Error(s"backups directory ${store.where()} is not usable: ${e.message}"))
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
    private def timeoutS = math.max(1, backups.timeoutSeconds)
    private def store: Store = storage.store(Location.Backups)

    /** `backups.pg-bin-dir` wins when set (homebrew's libpq is keg-only, so `pg_dump` is off PATH on a macOS dev box);
      * otherwise the search path. The failure message says exactly what to do.
      */
    private def exe(name: com.histmersion.util.data.backup.BackupService.Implementation.ExeName): Either[Api.Error, com.histmersion.util.data.backup.BackupService.Implementation.ExeResultPart2] =
      val key = Store.Key.applyUnsafe(name)
      Option(backups.pgBinDir).map(_.trim).filter(_.nonEmpty).flatMap(_ => storage.find(Location.PostgresTools)) match
        case Some(tools) if tools.isExecutable(key) => Right(tools.where(key))
        case Some(tools)                            =>
          Left(
            Api.Error(
              s"no executable $name at ${tools.where(key)} (histmersion.backups.pg-bin-dir / HISTMERSION_PG_BIN_DIR)"
            )
          )
        case None =>
          searchPath()
            .find(_.isExecutable(key))
            .map(_.where(key))
            .toRight(
              Api.Error(
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
    private def run(cmd: List[com.histmersion.util.data.backup.BackupService.Implementation.RunCmd]): Either[Api.Error, (com.histmersion.util.data.backup.BackupService.Implementation.RunResultPart2Part1, List[com.histmersion.util.data.backup.BackupService.Implementation.RunResultPart2Part2])] =
      val scratch = storage.scratch("histmersion-pg-")
      val log = Store.Key.applyUnsafe("pg.log")
      val cfg = settings()
      val launch = ProcessLaunch(
        cmd,
        environment = Map("PGPASSWORD" -> cfg.db.password, "PGUSER" -> cfg.db.user),
        output = ProcessOutput.WriteTo(scratch.store, log)
      )
      try
        scala.util
          .Try {
            val p = processes.start(launch).fold(e => throw java.io.IOException(e), identity)
            val ended = p.waitFor(timeoutS.toLong, TimeUnit.SECONDS)
            if !ended then
              p.destroyForcibly()
              val _ = p.waitFor(5, TimeUnit.SECONDS) // let the kill land before reading the log
              (Int.MinValue, tail(scratch.store, log))
            else (p.waitFor(), tail(scratch.store, log))
          }
          .toEither
          .left
          .map(e =>
            Api.Error(s"could not run ${cmd.headOption.getOrElse("")}: ${e.getClass.getSimpleName}: ${e.getMessage}")
          )
          .flatMap {
            case (Int.MinValue, _) => Left(Api.Error(s"${cmd.headOption.getOrElse("")} timed out after ${timeoutS}s"))
            case ok                => Right(ok)
          }
      finally scratch.close()

    /** The captured output, bounded: a `pg_restore` complaining about every statement can write megabytes, and only the
      * last handful of lines ever reaches an error message.
      */
    private def tail(logs: Store, log: Store.Key): List[com.histmersion.util.data.backup.BackupService.Implementation.TailResult] =
      scala.util
        .Try(logs.get(log).fold(List.empty[String])(_.text().linesIterator.toList.takeRight(200)))
        .getOrElse(Nil)

    private def target: Either[Api.Error, Backup.PgTarget] =
      Backup.parseJdbc(settings().db.url).left.map(m => Api.Error(s"cannot back up: $m"))

    /** A non-zero exit becomes an `ApiError` quoting the last output lines — the actual `pg_*` message, not a stack
      * trace.
      */
    private def succeeded(what: com.histmersion.util.data.backup.BackupService.Implementation.SucceededWhat, res: (com.histmersion.util.data.backup.BackupService.Implementation.SucceededResPart1, List[com.histmersion.util.data.backup.BackupService.Implementation.SucceededResPart2])): Either[Api.Error, Unit] =
      if res._1 == 0 then Right(())
      else Left(Api.Error(s"$what failed (exit ${res._1}): ${res._2.takeRight(5).mkString("; ")}"))

    /** `pg_dump -Fc` into the archive's reservation (`<name>.part`), moved into place only on success, so the listing
      * never shows a half-written archive.
      */
    def prepare(now: Instant): Either[Api.Error, PreparedBackup] =
      for
        t <- target
        pgDump <- exe("pg_dump")
        dir <- usable(store)
        name = freshName(dir, now)
      yield PreparedBackup(
        name,
        () =>
          val key = Store.Key.applyUnsafe(name)
          val started = System.nanoTime()
          for
            reservation <- dir.reserve(key, TempName.Suffixed(Pending)).left.map(e => Api.Error(e.message))
            stored <-
              try
                for
                  part <- reservation.onDisk.left.map(e => Api.Error(e.message))
                  res <- run(List(pgDump) ++ t.args ++ List("-Fc", "-f", part.toString))
                  _ <- succeeded("pg_dump", res)
                  done <- reservation
                    .commit()
                    .left
                    .map(e => Api.Error(s"could not finish ${dir.where(key)}: ${e.message}"))
                yield done
              finally reservation.close()
          yield Backup.Result(
            name,
            stored.size.value,
            dir.get(key).fold("")(_.sha256().value),
            (System.nanoTime() - started) / 1000000L,
            pruned(dir, backups.keep, keeping = name)
          )
      )

    def list: Either[Api.Error, List[Backup.Info]] = listing(store)

    def resolve(file: com.histmersion.util.data.backup.BackupService.Implementation.ResolveFile): Either[Api.Error, Store.Key] = resolving(store, file)

    def archive(file: com.histmersion.util.data.backup.BackupService.Implementation.ArchiveFile): Either[Api.Error, HandedFile] =
      val dir = store
      resolving(dir, file).flatMap(k => dir.onDiskFile(k).left.map(e => Api.Error(e.message)))

    /** `pg_restore --clean --if-exists`. NOTE: `pg_restore` exits 0 even when it skipped statements the server did not
      * understand (a dump taken by a newer `pg_dump` emits `SET transaction_timeout`, which Postgres < 17 rejects), so
      * its complaints are returned as warnings rather than swallowed.
      *
      * `--no-owner --no-privileges`: everything restored belongs to the role doing the restore, and the dump's GRANTs
      * are skipped, so a dump taken on an installation with other role names loads cleanly. The caller re-issues the
      * read-only role's grants afterwards.
      */
    def restore(archive: Store.Key): Either[Api.Error, (com.histmersion.util.data.backup.BackupService.Implementation.RestoreResultPart2Part1, List[com.histmersion.util.data.backup.BackupService.Implementation.RestoreResultPart2Part2])] =
      for
        t <- target
        pgRestore <- exe("pg_restore")
        file <- store.onDisk(archive).left.map(e => Api.Error(e.message))
        started = System.nanoTime()
        res <- run(
          List(pgRestore) ++ t.args ++
            List("--clean", "--if-exists", "--no-owner", "--no-privileges", file.toString)
        )
        warnings <- Backup.restoreOutcome(res._1, res._2).left.map(m => Api.Error(m))
      yield ((System.nanoTime() - started) / 1000000L, warnings)

    def guardedRows: com.histmersion.util.data.backup.BackupService.Implementation.GuardedRowsResult = transactor.read(guards(registry).map(g => BackupRepo.rows(g.table.value)).sum)

    def refusal(rows: com.histmersion.util.data.backup.BackupService.Implementation.RefusalRows): Api.Error = refusalOver(registry, rows)

  object Implementation:
    type ExeResultPart2 = ExeResultPart2.T
    object ExeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExeName = ExeName.T
    object ExeName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunResultPart2Part1 = RunResultPart2Part1.T
    object RunResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunResultPart2Part2 = RunResultPart2Part2.T
    object RunResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunCmd = RunCmd.T
    object RunCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TailResult = TailResult.T
    object TailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SucceededWhat = SucceededWhat.T
    object SucceededWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SucceededResPart1 = SucceededResPart1.T
    object SucceededResPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SucceededResPart2 = SucceededResPart2.T
    object SucceededResPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveFile = ResolveFile.T
    object ResolveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveFile = ArchiveFile.T
    object ArchiveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestoreResultPart2Part1 = RestoreResultPart2Part1.T
    object RestoreResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestoreResultPart2Part2 = RestoreResultPart2Part2.T
    object RestoreResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GuardedRowsResult = GuardedRowsResult.T
    object GuardedRowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefusalRows = RefusalRows.T
    object RefusalRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Dumps that are files of `store` holding `dumped` (no `pg_dump` runs), restores that change nothing, and guarded
    * rows that are `rows` (a test sets them). It lists, names, prunes and refuses as the database implementation does.
    */
  final class InMemory(
      store: Store,
      keep: Int,
      registry: DataContributions,
      rows: AtomicReference[Int] = AtomicReference(0),
      dumped: Array[Byte] = "PGDMP".getBytes("UTF-8")
  ) extends BackupService:
    def prepare(now: Instant): Either[Api.Error, PreparedBackup] =
      usable(store).map { dir =>
        val name = freshName(dir, now)
        PreparedBackup(
          name,
          () =>
            val key = Store.Key.applyUnsafe(name)
            dir
              .put(key, dumped)
              .left
              .map(e => Api.Error(s"could not finish ${dir.where(key)}: ${e.message}"))
              .map(s =>
                Backup.Result(name, s.size.value, dir.get(key).fold("")(_.sha256().value), 0L, pruned(dir, keep, name))
              )
        )
      }
    def list: Either[Api.Error, List[Backup.Info]] = listing(store)
    def resolve(file: com.histmersion.util.data.backup.BackupService.InMemory.ResolveFile): Either[Api.Error, Store.Key] = resolving(store, file)
    def archive(file: com.histmersion.util.data.backup.BackupService.InMemory.ArchiveFile): Either[Api.Error, HandedFile] =
      resolving(store, file).flatMap(k => store.onDiskFile(k).left.map(e => Api.Error(e.message)))
    def restore(archive: Store.Key): Either[Api.Error, (com.histmersion.util.data.backup.BackupService.InMemory.RestoreResultPart2Part1, List[com.histmersion.util.data.backup.BackupService.InMemory.RestoreResultPart2Part2])] =
      store.get(archive).toRight(Backup.missing(archive.value)).map(_ => (0L, Nil))
    def guardedRows: com.histmersion.util.data.backup.BackupService.InMemory.GuardedRowsResult = rows.get
    def refusal(n: com.histmersion.util.data.backup.BackupService.InMemory.RefusalN): Api.Error = refusalOver(registry, n)

  object InMemory:
    type ResolveFile = ResolveFile.T
    object ResolveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ArchiveFile = ArchiveFile.T
    object ArchiveFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestoreResultPart2Part1 = RestoreResultPart2Part1.T
    object RestoreResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestoreResultPart2Part2 = RestoreResultPart2Part2.T
    object RestoreResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GuardedRowsResult = GuardedRowsResult.T
    object GuardedRowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefusalN = RefusalN.T
    object RefusalN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
