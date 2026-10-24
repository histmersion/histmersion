package com.histmersion.server

import scala.util.chaining.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.io.File
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.{DigestInputStream, MessageDigest}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/** The maintenance lock (plan §1.10). A restore drops and recreates every table, so no other write
  * may run while it does: [[exclusively]] takes the flag, every write handler goes through [[guard]]
  * and is answered 503 while it is held.
  *
  * A plain `AtomicBoolean` is enough because there is exactly one server process per database (§2.5:
  * the server is the only DB client) and a restore is a single, whole-process operation. One instance
  * per [[Handlers]] — i.e. per server — rather than a global `object`, so two servers in one JVM
  * (which is what a test run is) do not freeze each other's writes. */
final class Maintenance:
  private val busy = AtomicBoolean(false)

  def active: Boolean = busy.get()

  /** Run `f` unless a restore is in progress, in which case answer 503. */
  def guard[A](op: String)(f: => Either[ApiError, A]): Either[ApiError, A] =
    if active then Left(Backup.maintenance(op)) else f

  /** Run `f` holding the lock; a second concurrent attempt is refused rather than queued. */
  def exclusively[A](op: String)(f: => Either[ApiError, A]): Either[ApiError, A] =
    if !busy.compareAndSet(false, true) then Left(Backup.maintenance(op))
    else
      try f
      finally busy.set(false)

/** `pg_dump` / `pg_restore` against the database THIS PROCESS is configured for (plan §1.10, §2.5:
  * the CLI is a pure HTTP client and holds no credentials, so the dump is taken here). Archives are
  * custom-format (`-Fc`, compressed, selective-restore capable) and land in `backups.dir`.
  *
  * Nothing here is a constant: the directory, the optional binary directory and the timeout all come
  * from `histmersion.backups.*`. */
final class BackupService(cfg: => AppConfig): // by name: read at each use (a config reload switches it)

  private def backups   = cfg.backups
  private def timeoutS  = math.max(1, backups.timeoutSeconds)
  private val createdAt = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

  /** Absolute, so a relative config value does not depend on where a handler thread thinks it is. */
  def dir: Path = Path.of(backups.dir).toAbsolutePath.normalize

  // ── locating the binaries ─────────────────────────────────────────────────────────────────────

  private def isExe(p: Path): Boolean = Files.isRegularFile(p) && Files.isExecutable(p)

  private def onPath(name: String): Option[Path] =
    Option(System.getenv("PATH")).toList
      .flatMap(_.split(File.pathSeparatorChar).toList)
      .filter(_.nonEmpty)
      .map(d => Path.of(d).resolve(name))
      .find(isExe)

  /** `backups.pg-bin-dir` wins when set (homebrew's libpq is keg-only, so `pg_dump` is off PATH on a
    * macOS dev box); otherwise PATH. The failure message says exactly what to do. */
  private def exe(name: String): Either[ApiError, String] =
    val configured = Option(backups.pgBinDir).map(_.trim).filter(_.nonEmpty).map(d => Path.of(d).resolve(name))
    configured match
      case Some(p) if isExe(p) => Right(p.toString)
      case Some(p)             => Left(ApiError(s"no executable $name at $p (histmersion.backups.pg-bin-dir / HISTMERSION_PG_BIN_DIR)"))
      case None =>
        onPath(name).map(_.toString).toRight(ApiError(
          s"$name is not on PATH — install the PostgreSQL client tools, or point " +
            s"HISTMERSION_PG_BIN_DIR at the directory holding them"))

  // ── running them ──────────────────────────────────────────────────────────────────────────────

  /** Run a command with `PGPASSWORD` set, capturing its merged output. Returns (exit code, output).
    *
    * The output goes to a FILE, not a pipe. Reading a pipe to EOF and *then* calling
    * `waitFor(timeout)` is not a timeout at all: the read only ends when the process closes its
    * stdout, i.e. when it exits — so `waitFor` was always called on an already-dead process and a
    * hung `pg_dump` hung the request forever. Redirecting to a file lets the process write freely
    * (no pipe-buffer deadlock either) while `waitFor` does the actual bounding, and the output is
    * read back afterwards. */
  private def run(cmd: List[String]): Either[ApiError, (Int, List[String])] =
    val log = Files.createTempFile("histmersion-pg-", ".log")
    val pb  = ProcessBuilder(cmd.asJava)
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.to(log.toFile))
    pb.environment.put("PGPASSWORD", cfg.db.password)
    pb.environment.put("PGUSER", cfg.db.user)
    scala.util.Try {
      val p     = pb.start()
      val ended = p.waitFor(timeoutS.toLong, TimeUnit.SECONDS)
      if !ended then
        p.destroyForcibly()
        val _ = p.waitFor(5, TimeUnit.SECONDS)      // let the kill land before reading the log
        (Int.MinValue, tail(log))
      else (p.exitValue, tail(log))
    }.toEither.left
      .map(e => ApiError(s"could not run ${cmd.headOption.getOrElse("")}: ${e.getClass.getSimpleName}: ${e.getMessage}"))
      .flatMap {
        case (Int.MinValue, _) => Left(ApiError(s"${cmd.headOption.getOrElse("")} timed out after ${timeoutS}s"))
        case ok                => Right(ok)
      }
      .map { r => val _ = Files.deleteIfExists(log); r }

  /** The captured output, bounded: a `pg_restore` complaining about every statement can write
    * megabytes, and only the last handful of lines ever reaches an error message. */
  private def tail(log: Path): List[String] =
    scala.util.Try(Files.readAllLines(log).asScala.toList.takeRight(200)).getOrElse(Nil)

  private def target: Either[ApiError, Backup.PgTarget] =
    Backup.parseJdbc(cfg.db.url).left.map(m => ApiError(s"cannot back up: $m"))

  private def ensureDir: Either[ApiError, Path] =
    scala.util.Try { val _ = Files.createDirectories(dir); dir }.toEither.left
      .map(e => ApiError(s"backups directory $dir is not usable: ${e.getMessage}"))

  // ── the operations ────────────────────────────────────────────────────────────────────────────

  /** `pg_dump -Fc` into `<dir>/histmersion-<UTC timestamp>.dump`. Written to a `.part` file and moved into
    * place only on success, so the listing never shows a half-written archive. */
  def create(now: Instant): Either[ApiError, BackupResult] = prepare(now).flatMap(_.run())

  /** A dump whose file name is fixed but which has not started — so a caller can report the name of
    * a backup that is still running (see [[BackupJobs]]). */
  final case class Prepared(file: String, run: () => Either[ApiError, BackupResult])

  def prepare(now: Instant): Either[ApiError, Prepared] =
    for
      t      <- target
      pgDump <- exe("pg_dump")
      _      <- ensureDir
      name    = Backup.uniqueName(Backup.fileName(now), n => Files.exists(dir.resolve(n)) || Files.exists(dir.resolve(s"$n.part")))
    yield Prepared(name, () =>
      val out     = dir.resolve(name)
      val part    = dir.resolve(s"$name.part")
      val started = System.nanoTime()
      for
        res <- run(List(pgDump) ++ t.args ++ List("-Fc", "-f", part.toString))
        _   <- succeeded("pg_dump", res, cleanUp = Some(part))
        _   <- scala.util.Try(Files.move(part, out, StandardCopyOption.ATOMIC_MOVE)).toEither.left
                 .map(e => ApiError(s"could not finish $out: ${e.getMessage}"))
      yield BackupResult(name, Files.size(out), sha256(out), (System.nanoTime() - started) / 1000000L,
        prune(keeping = name)))

  /** Delete the archives beyond `backups.keep` — AFTER a dump succeeded, never the one just written.
    * Best effort: a file that will not go is logged and stays; the backup itself still succeeded. */
  private def prune(keeping: String): List[String] =
    list.toOption.toList.flatMap { rows =>
      Backup.toPrune(rows.map(_.file), backups.keep).filterNot(_ == keeping).filter { n =>
        scala.util.Try(Files.deleteIfExists(dir.resolve(n))).fold(
          e => { scribe.warn(s"backup: could not prune $n: ${e.getMessage}"); false }, identity)
      }
    }

  /** A non-zero exit becomes an `ApiError` quoting the last output lines — the actual `pg_*` message,
    * not a stack trace (AGENTS: errors are actionable text). */
  private def succeeded(what: String, res: (Int, List[String]), cleanUp: Option[Path] = None)
      : Either[ApiError, Unit] =
    if res._1 == 0 then Right(())
    else
      cleanUp.foreach(p => { val _ = Files.deleteIfExists(p) })
      Left(ApiError(s"$what failed (exit ${res._1}): ${res._2.takeRight(5).mkString("; ")}"))

  /** The archives in `backups.dir`, newest first by when they were taken ([[Backup.newestFirst]]). */
  def list: Either[ApiError, List[BackupInfo]] =
    if !Files.isDirectory(dir) then Right(Nil)
    else
      scala.util.Try {
        val s = Files.list(dir)
        try
          s.iterator.asScala
            .filter(Files.isRegularFile(_))
            .flatMap(p => Backup.safeName(p.getFileName.toString).toOption.map(n => (n, p)))
            .map((n, p) => BackupInfo(n, Files.size(p),
              createdAt.format(Backup.takenAt(n, Files.getLastModifiedTime(p).toInstant))))
            .toList
            .pipe(Backup.newestFirst)
        finally s.close()
      }.toEither.left.map(e => ApiError(s"cannot list $dir: ${e.getMessage}"))

  /** The ONLY way a client-supplied name becomes a path: syntactically safe AND present in the
    * listing. A name that is neither cannot escape `backups.dir`. */
  def resolve(file: String): Either[ApiError, Path] =
    for
      name  <- Backup.safeName(file).left.map(m => ApiError(m))
      known <- list
      _     <- if known.exists(_.file == name) then Right(()) else Left(Backup.missing(name))
    yield dir.resolve(name)

  /** `pg_restore --clean --if-exists`. NOTE: `pg_restore` exits 0 even when it skipped statements the
    * server did not understand (a dump taken by a newer `pg_dump` emits `SET transaction_timeout`,
    * which Postgres < 17 rejects), so its complaints are returned as warnings rather than swallowed.
    *
    * `--no-owner --no-privileges`: everything restored belongs to the role doing the restore, and the
    * dump's GRANTs are skipped, so a dump taken on an installation with other role names loads cleanly.
    * The caller re-issues the read-only role's grants afterwards. */
  def restore(file: Path): Either[ApiError, (Long, List[String])] =
    for
      t         <- target
      pgRestore <- exe("pg_restore")
      started    = System.nanoTime()
      res       <- run(List(pgRestore) ++ t.args ++
                     List("--clean", "--if-exists", "--no-owner", "--no-privileges", file.toString))
      warnings  <- Backup.restoreOutcome(res._1, res._2).left.map(m => ApiError(m))
    yield ((System.nanoTime() - started) / 1000000L, warnings)

  /** Streamed, so hashing a multi-gigabyte archive costs one buffer rather than one heap. */
  private def sha256(p: Path): String =
    val md = MessageDigest.getInstance("SHA-256")
    val in = DigestInputStream(Files.newInputStream(p), md)
    try
      val buf = new Array[Byte](1 << 16)
      while in.read(buf) >= 0 do ()
    finally in.close()
    md.digest.map(b => f"$b%02x").mkString

/** Backups run as a JOB, not inside the HTTP request. A dump of the corpus takes minutes, and the
  * request that started it used to be cut at 60 s (by the server and by any proxy in between): the
  * handler thread was interrupted and the dump was lost a few KB before its end, every time. Now the
  * dump runs on its own daemon thread; the request waits up to `waitSeconds` and otherwise answers
  * `running`, and `GET /api/backup/status` reports the job until it is done or failed.
  *
  * One job at a time: a second `start` while one runs joins it rather than dumping twice. */
final class BackupJobs(service: BackupService, waitSeconds: => Int,
    pins: com.histmersion.server.hot.Pinning = com.histmersion.server.hot.Pinning.none):
  import java.util.concurrent.{CompletableFuture, TimeoutException}
  import java.util.concurrent.atomic.AtomicReference

  private final case class Job(file: String, started: Long, result: CompletableFuture[Either[ApiError, BackupResult]])
  private val current = AtomicReference[Option[Job]](None)

  def running: Boolean = current.get().exists(!_.result.isDone)

  /** Start a dump (or join the running one) and wait up to `waitSeconds` for it. */
  def start(now: Instant): Either[ApiError, BackupStatus] =
    val job = current.get() match
      case Some(j) if !j.result.isDone => Right(j)
      case _ =>
        service.prepare(now).map { p =>
          val f = CompletableFuture[Either[ApiError, BackupResult]]()
          val j = Job(p.file, System.nanoTime(), f)
          current.set(Some(j))
          // the dump runs on the generation (database) the request was pinned to
          val work = pins.carry(f.complete(scala.util.Try(p.run()).toEither.left.map(e => ApiError(s"backup crashed: ${e.getMessage}")).flatten))
          val _ = Thread.ofPlatform().daemon(true).name("histmersion-backup").start(() => { val _ = work.run() })
          j
        }
    job.map { j =>
      try { val _ = j.result.get(math.max(0, waitSeconds).toLong, TimeUnit.SECONDS) }
      catch case _: TimeoutException => ()
      statusOf(j)
    }

  /** The running job, or the last one this server ran (`None` before the first). */
  def status: Option[BackupStatus] = current.get().map(statusOf)

  private def statusOf(j: Job): BackupStatus =
    val elapsed = (System.nanoTime() - j.started) / 1000000L
    if !j.result.isDone then BackupStatus.running(j.file, elapsed)
    else j.result.get() match
      case Right(r) => BackupStatus.done(r)
      case Left(e)  => BackupStatus.failed(j.file, elapsed, e.message)
