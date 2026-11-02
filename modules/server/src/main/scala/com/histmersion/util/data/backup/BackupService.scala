package com.histmersion.util.data.backup

import com.histmersion.api.ApiError
import com.histmersion.api.backup.{Backup, BackupInfo, BackupResult}
import com.histmersion.config.AppConfig
import com.histmersion.util.data.DataContributions
import com.histmersion.util.database.Transactor
import com.histmersion.util.process.{Processes, ProcessLaunch, ProcessOutput}
import com.histmersion.util.storage.{HandedFile, Location, Storage, Store, StoreKey, TempName}

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The database's dumps: `pg_dump` / `pg_restore` against the database this server is configured for (the CLI holds no
  * credentials, so the dump is taken here), the archives in the backups store (`backups.dir`), their listing and
  * housekeeping (`backups.keep`), and what a restore must not drop unasked — the registry's guarded tables. Archives
  * are custom-format (`-Fc`: compressed, selective-restore capable).
  */
trait BackupService:

  /** A dump into `histmersion-<UTC timestamp>.dump`, named but not started. */
  def prepare(now: Instant): Either[ApiError, PreparedBackup]

  /** A dump, run to its end. */
  def create(now: Instant): Either[ApiError, BackupResult] = prepare(now).flatMap(_.run())

  /** The archives, newest first by when they were taken ([[Backup.newestFirst]]). */
  def list: Either[ApiError, List[BackupInfo]]

  /** The ONLY way a client-supplied name names an archive: syntactically safe AND present in the listing. A name that
    * is neither cannot escape the store.
    */
  def resolve(file: String): Either[ApiError, StoreKey]

  /** The archive `file` itself, for the download (`GET /api/backups/{file}`). */
  def archive(file: String): Either[ApiError, HandedFile]

  /** `pg_restore --clean --if-exists` of `archive`: how long it took, and what `pg_restore` complained of. */
  def restore(archive: StoreKey): Either[ApiError, (Long, List[String])]

  /** The rows the guarded tables hold: a restore over any of them needs `force`. */
  def guardedRows: Int

  /** The refusal of a restore over `rows` guarded rows. */
  def refusal(rows: Int): ApiError

object BackupService:

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

  private def refusalOver(registry: DataContributions, rows: Int): ApiError =
    val noun = guards(registry).map(_.noun).distinct.mkString("/")
    ApiError(s"the database still holds $rows $noun(s); restoring would drop them — re-run with force")

  /** The archives of `store`, newest first; `where` names it in the error. */
  private def listing(store: Store): Either[ApiError, List[BackupInfo]] =
    if !store.isDirectory(StoreKey.Root) then Right(Nil)
    else
      scala.util
        .Try(
          Backup.newestFirst(
            store
              .names()
              .map(StoreKey.of(_))
              .collect { case Right(k) if store.isFile(k) => k }
              .flatMap(k => Backup.safeName(k.name).toOption.flatMap(n => store.stat(k).map(s => (n, s))))
              .map((n, s) => BackupInfo(n, s.size.value, CreatedAt.format(Backup.takenAt(n, s.modified))))
          )
        )
        .toEither
        .left
        .map(e => ApiError(s"cannot list ${store.where()}: ${e.getMessage}"))

  private def resolving(store: Store, file: String): Either[ApiError, StoreKey] =
    for
      name <- Backup.safeName(file).left.map(m => ApiError(m))
      known <- listing(store)
      _ <- if known.exists(_.file == name) then Right(()) else Left(Backup.missing(name))
      key <- StoreKey.either(name).left.map(m => ApiError(m))
    yield key

  /** The name of a new archive taken at `now`, not taken by an archive nor by a dump being written. */
  private def freshName(store: Store, now: Instant): String =
    Backup.uniqueName(
      Backup.fileName(now),
      n => store.exists(StoreKey.applyUnsafe(n)) || store.exists(StoreKey.applyUnsafe(n + Pending))
    )

  /** Delete the archives beyond `keep` — AFTER a dump succeeded, never the one just written. Best effort: a file that
    * will not go is logged and stays; the backup itself still succeeded.
    */
  private def pruned(store: Store, keep: Int, keeping: String): List[String] =
    listing(store).toOption.toList.flatMap { rows =>
      Backup.toPrune(rows.map(_.file), keep).filterNot(_ == keeping).filter { n =>
        scala.util
          .Try(store.delete(StoreKey.applyUnsafe(n)))
          .fold(e => { scribe.warn(s"backup: could not prune $n: ${e.getMessage}"); false }, identity)
      }
    }

  private def usable(store: Store): Either[ApiError, Store] =
    store
      .makeDirectory()
      .left
      .map(e => ApiError(s"backups directory ${store.where()} is not usable: ${e.message}"))
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
    private def exe(name: String): Either[ApiError, String] =
      val key = StoreKey.applyUnsafe(name)
      Option(backups.pgBinDir).map(_.trim).filter(_.nonEmpty).flatMap(_ => storage.find(Location.PostgresTools)) match
        case Some(tools) if tools.isExecutable(key) => Right(tools.where(key))
        case Some(tools)                            =>
          Left(
            ApiError(
              s"no executable $name at ${tools.where(key)} (histmersion.backups.pg-bin-dir / HISTMERSION_PG_BIN_DIR)"
            )
          )
        case None =>
          searchPath()
            .find(_.isExecutable(key))
            .map(_.where(key))
            .toRight(
              ApiError(
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
    private def run(cmd: List[String]): Either[ApiError, (Int, List[String])] =
      val scratch = storage.scratch("histmersion-pg-")
      val log = StoreKey.applyUnsafe("pg.log")
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
            ApiError(s"could not run ${cmd.headOption.getOrElse("")}: ${e.getClass.getSimpleName}: ${e.getMessage}")
          )
          .flatMap {
            case (Int.MinValue, _) => Left(ApiError(s"${cmd.headOption.getOrElse("")} timed out after ${timeoutS}s"))
            case ok                => Right(ok)
          }
      finally scratch.close()

    /** The captured output, bounded: a `pg_restore` complaining about every statement can write megabytes, and only the
      * last handful of lines ever reaches an error message.
      */
    private def tail(logs: Store, log: StoreKey): List[String] =
      scala.util
        .Try(logs.get(log).fold(List.empty[String])(_.text().linesIterator.toList.takeRight(200)))
        .getOrElse(Nil)

    private def target: Either[ApiError, Backup.PgTarget] =
      Backup.parseJdbc(settings().db.url).left.map(m => ApiError(s"cannot back up: $m"))

    /** A non-zero exit becomes an `ApiError` quoting the last output lines — the actual `pg_*` message, not a stack
      * trace.
      */
    private def succeeded(what: String, res: (Int, List[String])): Either[ApiError, Unit] =
      if res._1 == 0 then Right(())
      else Left(ApiError(s"$what failed (exit ${res._1}): ${res._2.takeRight(5).mkString("; ")}"))

    /** `pg_dump -Fc` into the archive's reservation (`<name>.part`), moved into place only on success, so the listing
      * never shows a half-written archive.
      */
    def prepare(now: Instant): Either[ApiError, PreparedBackup] =
      for
        t <- target
        pgDump <- exe("pg_dump")
        dir <- usable(store)
        name = freshName(dir, now)
      yield PreparedBackup(
        name,
        () =>
          val key = StoreKey.applyUnsafe(name)
          val started = System.nanoTime()
          for
            reservation <- dir.reserve(key, TempName.Suffixed(Pending)).left.map(e => ApiError(e.message))
            stored <-
              try
                for
                  part <- reservation.onDisk.left.map(e => ApiError(e.message))
                  res <- run(List(pgDump) ++ t.args ++ List("-Fc", "-f", part.toString))
                  _ <- succeeded("pg_dump", res)
                  done <- reservation
                    .commit()
                    .left
                    .map(e => ApiError(s"could not finish ${dir.where(key)}: ${e.message}"))
                yield done
              finally reservation.close()
          yield BackupResult(
            name,
            stored.size.value,
            dir.get(key).fold("")(_.sha256().value),
            (System.nanoTime() - started) / 1000000L,
            pruned(dir, backups.keep, keeping = name)
          )
      )

    def list: Either[ApiError, List[BackupInfo]] = listing(store)

    def resolve(file: String): Either[ApiError, StoreKey] = resolving(store, file)

    def archive(file: String): Either[ApiError, HandedFile] =
      val dir = store
      resolving(dir, file).flatMap(k => dir.onDiskFile(k).left.map(e => ApiError(e.message)))

    /** `pg_restore --clean --if-exists`. NOTE: `pg_restore` exits 0 even when it skipped statements the server did not
      * understand (a dump taken by a newer `pg_dump` emits `SET transaction_timeout`, which Postgres < 17 rejects), so
      * its complaints are returned as warnings rather than swallowed.
      *
      * `--no-owner --no-privileges`: everything restored belongs to the role doing the restore, and the dump's GRANTs
      * are skipped, so a dump taken on an installation with other role names loads cleanly. The caller re-issues the
      * read-only role's grants afterwards.
      */
    def restore(archive: StoreKey): Either[ApiError, (Long, List[String])] =
      for
        t <- target
        pgRestore <- exe("pg_restore")
        file <- store.onDisk(archive).left.map(e => ApiError(e.message))
        started = System.nanoTime()
        res <- run(
          List(pgRestore) ++ t.args ++
            List("--clean", "--if-exists", "--no-owner", "--no-privileges", file.toString)
        )
        warnings <- Backup.restoreOutcome(res._1, res._2).left.map(m => ApiError(m))
      yield ((System.nanoTime() - started) / 1000000L, warnings)

    def guardedRows: Int = transactor.read(guards(registry).map(g => BackupRepo.rows(g.table.value)).sum)

    def refusal(rows: Int): ApiError = refusalOver(registry, rows)

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
    def prepare(now: Instant): Either[ApiError, PreparedBackup] =
      usable(store).map { dir =>
        val name = freshName(dir, now)
        PreparedBackup(
          name,
          () =>
            val key = StoreKey.applyUnsafe(name)
            dir
              .put(key, dumped)
              .left
              .map(e => ApiError(s"could not finish ${dir.where(key)}: ${e.message}"))
              .map(s =>
                BackupResult(name, s.size.value, dir.get(key).fold("")(_.sha256().value), 0L, pruned(dir, keep, name))
              )
        )
      }
    def list: Either[ApiError, List[BackupInfo]] = listing(store)
    def resolve(file: String): Either[ApiError, StoreKey] = resolving(store, file)
    def archive(file: String): Either[ApiError, HandedFile] =
      resolving(store, file).flatMap(k => store.onDiskFile(k).left.map(e => ApiError(e.message)))
    def restore(archive: StoreKey): Either[ApiError, (Long, List[String])] =
      store.get(archive).toRight(Backup.missing(archive.value)).map(_ => (0L, Nil))
    def guardedRows: Int = rows.get
    def refusal(n: Int): ApiError = refusalOver(registry, n)
