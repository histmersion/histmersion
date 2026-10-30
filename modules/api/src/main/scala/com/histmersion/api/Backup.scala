package com.histmersion.api

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Backup / restore (plan §1.10) — the PURE half: file naming, the one gate between a client-supplied name and the
  * filesystem, and the JDBC-URL → `pg_dump` arguments translation.
  *
  * The dump itself is taken SERVER-side (`com.histmersion.server.BackupService`): the CLI is a pure HTTP client and
  * deliberately holds no database credentials, so `pg_dump`/`pg_restore` run in the process that has them. Archives are
  * `pg_dump -Fc` (custom, compressed, `pg_restore`-able).
  */
object Backup:

  val Prefix = "histmersion-"
  val Extension = ".dump"

  /** UTC, second resolution, no separators — so the names sort chronologically as plain text and survive every
    * filesystem: `histmersion-20260825T145112Z.dump`.
    */
  private val Stamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  def timestamp(at: Instant): String = Stamp.format(at.truncatedTo(ChronoUnit.SECONDS))

  def fileName(at: Instant): String = s"$Prefix${timestamp(at)}$Extension"

  /** Two backups inside the same second would otherwise pick the same name and the second would silently overwrite the
    * first: `histmersion-…Z.dump` → `histmersion-…Z-2.dump` → `-3` … `taken` is the caller's "does this file already
    * exist" (the filesystem, or a test's set).
    */
  def uniqueName(name: String, taken: String => Boolean): String =
    if !taken(name) then name
    else
      val stem = name.dropRight(Extension.length)
      LazyList.from(2).map(n => s"$stem-$n$Extension").find(c => !taken(c)).getOrElse(name)

  private val Written = """histmersion-(\d{8}T\d{6}Z)(?:-(\d+))?\.dump""".r

  /** The archives beyond the newest `keep`, oldest first: what a finished dump prunes. Only names this service writes
    * count (`histmersion-<stamp>[-n].dump`; anything else in the directory is someone's own file and stays), and the
    * order is the stamp then the same-second suffix — as plain text `…Z-2.dump` sorts BEFORE `…Z.dump`. `keep <= 0`
    * keeps everything.
    */
  def toPrune(names: List[String], keep: Int): List[String] =
    if keep <= 0 then Nil
    else
      val ours = names.collect { case n @ Written(stamp, suffix) =>
        (n, (stamp, Option(suffix).map(_.toInt).getOrElse(1)))
      }
      ours.sortBy(_._2).reverse.drop(keep).map(_._1).reverse

  /** A UTC stamp anywhere in a name, with its same-second suffix — `keep-before-schema-024- 20260925T085045Z.dump`
    * carries one too, though it is not a name this service writes.
    */
  private val AnyStamp = """(\d{8}T\d{6}Z)(?:-(\d+))?""".r.unanchored

  /** When an archive was taken: the stamp in its name when it has one (a copy keeps that, not its mtime), else the
    * file's `mtime`. Pure.
    */
  def takenAt(name: String, mtime: Instant): Instant = name match
    case AnyStamp(stamp, _) =>
      scala.util.Try(Instant.from(Stamp.parse(stamp))).getOrElse(mtime)
    case _ => mtime

  /** Newest first BY TIME (#772): sorting by name put `keep-before-…` above every `histmersion-…` archive, and `about`
    * reported it as the last backup. Ties (one second) go to the higher same-second suffix, then the name. `createdAt`
    * is ISO-8601, as [[BackupInfo]] carries it. Pure.
    */
  def newestFirst(bs: List[BackupInfo]): List[BackupInfo] =
    def suffix(n: String) = n match
      case AnyStamp(_, sfx) => Option(sfx).flatMap(_.toIntOption).getOrElse(1)
      case _                => 0
    def at(b: BackupInfo) = scala.util.Try(Instant.parse(b.createdAt)).getOrElse(Instant.EPOCH)
    bs.sortBy(b => (at(b), suffix(b.file), b.file)).reverse

  /** Deliberately strict: one path segment, starting with an alphanumeric (so `..` and dotfiles are out), no separator
    * of either flavour, no NUL, ending in [[Extension]]. Callers additionally check the name against the LISTING, so
    * only a file we ourselves wrote can be read or restored.
    */
  private val Safe = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  /** The ONE gate between a client-supplied name and the filesystem. */
  def safeName(raw: String): Either[String, String] =
    val n = Option(raw).map(_.trim).getOrElse("")
    if n.isEmpty then Left("no backup file given")
    else if n.length > 128 then Left(s"backup file name is too long (${n.length} chars)")
    else if !n.endsWith(Extension) then Left(s"'$n' is not a $Extension file")
    else if n.length == Extension.length then Left(s"'$n' has no name before $Extension")
    else if !Safe.matches(n) then
      Left(s"'$n' is not a plain backup file name — no directories, no '..', letters/digits/._- only")
    else Right(n)

  /** Everything `pg_dump`/`pg_restore` need on the command line to reach the same database the server is connected to.
    * `host`/`port` are optional: absent from the JDBC URL means "let libpq decide" (its own defaults / `PGHOST` /
    * `PGPORT`), which is also why no port literal appears here.
    */
  final case class PgTarget(host: Option[String], port: Option[Int], database: String):
    /** `-h host -p port -d db`, plus `-w` so a missing password fails instead of prompting forever. */
    def args: List[String] =
      host.toList.flatMap(h => List("-h", h)) ++
        port.toList.flatMap(p => List("-p", p.toString)) ++
        List("-d", database, "-w")

  private val JdbcScheme = "jdbc:postgresql:"

  /** `jdbc:postgresql://host:port/db?opts` → [[PgTarget]]. Also accepts the host-less `jdbc:postgresql:db` form and
    * bracketed IPv6 authorities.
    */
  def parseJdbc(url: String): Either[String, PgTarget] =
    val u = Option(url).map(_.trim).getOrElse("")
    if !u.startsWith(JdbcScheme) then Left(s"not a PostgreSQL JDBC URL: '$u'")
    else
      val rest = u.drop(JdbcScheme.length).takeWhile(_ != '?')
      val (authority, db) =
        if !rest.startsWith("//") then ("", rest)
        else
          val a = rest.drop(2)
          a.indexOf('/') match
            case -1 => (a, "")
            case i  => (a.take(i), a.drop(i + 1))
      // an IPv6 literal is bracketed, so only a ':' AFTER the closing bracket introduces a port
      val colon = authority.lastIndexOf(':')
      val hasPort = colon > authority.lastIndexOf(']')
      val (host, portText) =
        if colon >= 0 && hasPort then (authority.take(colon), Some(authority.drop(colon + 1)))
        else (authority, None)
      val port: Either[String, Option[Int]] = portText match
        case None    => Right(None)
        case Some(p) => p.toIntOption.map(Some(_)).toRight(s"'$p' is not a port number in '$u'")
      for
        name <- Some(db.trim).filter(_.nonEmpty).toRight(s"JDBC URL has no database name: '$u'")
        p <- port
      yield PgTarget(Some(host).filter(_.nonEmpty), p, name)

  private val IgnoredErrors = """errors ignored on restore:\s*(\d+)""".r

  /** How to read `pg_restore`'s exit code — the one genuinely subtle part of a restore.
    *
    * `pg_restore` exits 1 whenever it skipped ANY statement, even one it deliberately continued past, and then says so
    * on its last line: `warning: errors ignored on restore: N`. That happens routinely on a version skew — an archive
    * written by a NEWER `pg_dump` starts with `SET transaction_timeout = 0`, which Postgres < 17 rejects — while the
    * data itself restores perfectly. Failing the whole operation there would be wrong; silently returning success would
    * be worse. So: a run that reached the "errors ignored" summary SUCCEEDED with warnings, anything else that exits
    * non-zero FAILED. (The caller additionally re-validates the schema with Flyway, which is what catches a restore
    * that broke something real.)
    *
    * Right = the notable output lines, to report as warnings. Left = an actionable failure message.
    */
  def restoreOutcome(exit: Int, output: List[String]): Either[String, List[String]] =
    val notable = output
      .map(_.trim)
      .filter(_.nonEmpty)
      .filter(l => l.toLowerCase.contains("error") || l.toLowerCase.contains("warning"))
    val completed = output.exists(l => IgnoredErrors.findFirstMatchIn(l).isDefined)
    if exit == 0 || completed then Right(notable)
    else Left(s"pg_restore failed (exit $exit): ${notable.takeRight(5).mkString("; ")}")

  // ── error codes shared by the endpoints ───────────────────────────────────────────────────────

  /** A write arrived while a restore held the maintenance lock → 503 (see `Api.writeErrors`). */
  val CodeMaintenance = "maintenance"

  def maintenance(op: String): ApiError =
    ApiError(s"$op is unavailable: a restore is in progress on this server", Some(CodeMaintenance))

  def missing(file: String): ApiError =
    ApiError(s"no backup named '$file' in the backups directory", Some(Write.CodeNotFound))

  /** `1.4 GB` / `812.0 kB` / `37 B` — decimal units, which is what `ls -h` on a dump reports too.
    *
    * Formatted against `Locale.ROOT`, NOT the default locale: this string goes onto the wire in a Markdown API
    * response, so the decimal separator must not depend on the machine's locale. (JDK 25 ships a CLDR in which `en_PL`
    * — an English UI in Poland — takes its number symbols from the region and renders `%.1f` as `2,5`; JDK 22 rendered
    * `2.5`. The value is data, not UI.)
    */
  def humanBytes(n: Long): String =
    val units = Vector("kB", "MB", "GB", "TB")
    if n < 1000 then s"$n B"
    else
      val exp = math.min(units.size, (math.log(n.toDouble) / math.log(1000)).toInt)
      val v = n.toDouble / math.pow(1000, exp.toDouble)
      String.format(Locale.ROOT, "%.1f %s", v, units(exp - 1))

/** Where a backup job is. On the wire as its lower-case name (`BackupStatus.state`). */
enum BackupState:
  case Running, Done, Failed
  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object BackupState:
  def parse(s: String): Option[BackupState] = values.find(_.wire == s)

// ── wire DTOs (declared last: Kindlings derivation is order-sensitive at the file level) ────────

/** `POST /api/backup` — one freshly written archive. */
final case class BackupResult(
    file: String,
    bytes: Long,
    sha256: String,
    durationMs: Long,
    /** Older archives deleted afterwards to keep `backups.keep`. */
    pruned: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupResult:
  given Schema[BackupResult] = summon[KindlingsSchema[BackupResult]].schema

/** One row of `GET /api/backups`, newest first. No checksum here on purpose — hashing every archive on every listing
  * would read gigabytes; `POST /api/backup` reports the checksum of what it wrote. `createdAt` is when the dump was
  * taken ([[Backup.takenAt]]).
  */
final case class BackupInfo(file: String, bytes: Long, createdAt: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BackupInfo:
  given Schema[BackupInfo] = summon[KindlingsSchema[BackupInfo]].schema

/** `POST /api/restore`. `force` is required whenever the database still holds sources — a restore is
  * `pg_restore --clean --if-exists`, i.e. it DROPS what is there.
  */
final case class RestoreRequest(file: String, force: Boolean = false) derives KindlingsCodecAsObject, KindlingsSchema
object RestoreRequest:
  given Schema[RestoreRequest] = summon[KindlingsSchema[RestoreRequest]].schema

/** The outcome of a restore: the schema version Flyway validated afterwards, how many sources the database held before
  * and after, and any line `pg_restore` complained about (it exits 0 with "errors ignored on restore" when a dump was
  * taken by a NEWER pg_dump than the server).
  */
final case class RestoreResult(
    file: String,
    schemaVersion: String,
    sourcesBefore: Int,
    sourcesAfter: Int,
    durationMs: Long,
    warnings: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object RestoreResult:
  given Schema[RestoreResult] = summon[KindlingsSchema[RestoreResult]].schema

/** `POST /api/backup` and `GET /api/backup/status`: a backup job, which may still be running. `state` is a
  * [[BackupState]]; `bytes`/`sha256` are set once it is done, `error` once it failed.
  */
final case class BackupStatus(
    file: String,
    state: String,
    durationMs: Long,
    bytes: Option[Long] = None,
    sha256: Option[String] = None,
    error: Option[String] = None,
    pruned: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def stateOf: Option[BackupState] = BackupState.parse(state)
  def result: Option[BackupResult] =
    for b <- bytes; h <- sha256 if stateOf.contains(BackupState.Done) yield BackupResult(file, b, h, durationMs, pruned)

object BackupStatus:
  given Schema[BackupStatus] = summon[KindlingsSchema[BackupStatus]].schema
  def running(file: String, elapsedMs: Long): BackupStatus = BackupStatus(file, BackupState.Running.wire, elapsedMs)
  def done(r: BackupResult): BackupStatus =
    BackupStatus(r.file, BackupState.Done.wire, r.durationMs, Some(r.bytes), Some(r.sha256), pruned = r.pruned)
  def failed(file: String, elapsedMs: Long, error: String): BackupStatus =
    BackupStatus(file, BackupState.Failed.wire, elapsedMs, error = Some(error))
