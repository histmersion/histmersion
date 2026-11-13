package com.histmersion
package api.backup

import com.histmersion.util.problem.{Legacy, Problem}

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Api
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.api.Sha256

object Backup:
  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type JdbcUrl = JdbcUrl.T
  object JdbcUrl extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]
  type Retention = Retention.T
  object Retention extends Newtype[Int, Pure]
  type ToolOutput = ToolOutput.T
  object ToolOutput extends Newtype[String, Pure]

  val Prefix = "histmersion-"
  val Extension = ".dump"

  /** No separators, so the names sort chronologically as plain text and are valid on every filesystem. */
  private val Stamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  def timestamp(at: Instant): String = (Stamp.format(at.truncatedTo(ChronoUnit.SECONDS)))

  def fileName(at: Instant): Result.File = Result.File(s"$Prefix${timestamp(at)}$Extension")

  def uniqueName(name: Name, taken: Result.File => Boolean): Result.File =
    if !((taken(Result.File((name).value))) == (true)) then Result.File((name).value)
    else
      val stem = (name).value.dropRight(Extension.length)
      LazyList
        .from(2)
        .map(n => Result.File(s"$stem-$n$Extension"))
        .find(c => !((taken(c)) == (true)))
        .getOrElse(Result.File((name).value))

  private val Written = """histmersion-(\d{8}T\d{6}Z)(?:-(\d+))?\.dump""".r

  /** Only names this service writes count: anything else in the directory is someone's own file. As plain text
    * `…Z-2.dump` sorts before `…Z.dump`, so the order is the stamp, then the same-second suffix.
    */
  def toPrune(names: List[Result.File], keep: Retention): List[Result.File] =
    if keep.value <= 0 then Nil
    else
      val ours = names.flatMap { n =>
        n.value match
          case Written(stamp, suffix) => List((n, (stamp, Option(suffix).map(_.toInt).getOrElse(1))))
          case _                      => Nil
      }
      ours.sortBy(_._2).reverse.drop(keep.value).map(_._1).reverse

  private val AnyStamp = """(\d{8}T\d{6}Z)(?:-(\d+))?""".r.unanchored

  /** The stamp in the name wins over the mtime: a copied archive keeps its name but not its mtime. */
  def takenAt(name: Result.File, mtime: Instant): Instant = name.value match
    case AnyStamp(stamp, _) =>
      scala.util.Try(Instant.from(Stamp.parse(stamp))).getOrElse(mtime)
    case _ => mtime

  def newestFirst(bs: List[Info]): List[Info] =
    def suffix(n: String) = n match
      case AnyStamp(_, sfx) => Option(sfx).flatMap(_.toIntOption).getOrElse(1)
      case _                => 0
    def at(b: Info) =
      scala.util.Try(Instant.parse(b.createdAt.value)).getOrElse(Instant.EPOCH)
    bs.sortBy(b => (at(b), suffix(b.file.value), b.file.value: String)).reverse

  private val Safe = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def safeName(raw: Name): Either[Problem, Result.File] =
    val n = Option(raw).map((_).value.trim).getOrElse("")
    if n.isEmpty then Left(Problem.Unclassified(Legacy.Text("no backup file given")))
    else if n.length > 128 then
      Left(Problem.Unclassified(Legacy.Text(s"backup file name is too long (${n.length} chars)")))
    else if !n.endsWith(Extension) then Left(Problem.Unclassified(Legacy.Text(s"'$n' is not a $Extension file")))
    else if n.length == Extension.length then
      Left(Problem.Unclassified(Legacy.Text(s"'$n' has no name before $Extension")))
    else if !Safe.matches(n) then
      Left(
        Problem.Unclassified(
          Legacy.Text(s"'$n' is not a plain backup file name — no directories, no '..', letters/digits/._- only")
        )
      )
    else Right(Result.File(n))

  /** Absent `host`/`port` let libpq decide (its own defaults, `PGHOST`, `PGPORT`). */
  final case class PgTarget(host: Option[PgTarget.Host], port: Option[PgTarget.Port], database: PgTarget.Database):
    /** `-w`: a missing password fails instead of `pg_dump` prompting forever. */
    def args: List[String] =
      host.toList.flatMap(h => List("-h", h.value)) ++
        port.toList.flatMap(p => List("-p", p.value.toString)) ++
        List("-d", database.value, "-w")

  object PgTarget:

    type Host = Host.T
    object Host extends Newtype[String, Pure]
    type Port = Port.T
    object Port extends Newtype[Int, Pure]
    type Database = Database.T
    object Database extends Newtype[String, Pure]

  private val JdbcScheme = "jdbc:postgresql:"

  def parseJdbc(url: JdbcUrl): Either[Problem, PgTarget] =
    val u = Option(url).map((_).value.trim).getOrElse("")
    if !u.startsWith(JdbcScheme) then Left(Problem.Unclassified(Legacy.Text(s"not a PostgreSQL JDBC URL: '$u'")))
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
      val port: Either[Problem, Option[PgTarget.Port]] = portText match
        case None    => Right(None)
        case Some(p) =>
          ((p.toIntOption
            .map(Some(_))
            .toRight(s"'$p' is not a port number in '$u'"))
            .left
            .map(refactorError => Problem.Unclassified(Legacy.Text(refactorError))))
            .map(refactorValue2 => (refactorValue2).map(refactorValue3 => PgTarget.Port(refactorValue3)))
      for
        name <- Some(db.trim)
          .filter(_.nonEmpty)
          .toRight(Problem.Unclassified(Legacy.Text(s"JDBC URL has no database name: '$u'")))
        p <- port
      yield PgTarget(
        (Some(host).filter(_.nonEmpty)).map(refactorValue10 => PgTarget.Host(refactorValue10)),
        p,
        PgTarget.Database(name)
      )

  private val IgnoredErrors = """errors ignored on restore:\s*(\d+)""".r

  /** `pg_restore` exits 1 whenever it skipped any statement, even one it continued past, and then ends with `warning:
    * errors ignored on restore: N`. That happens routinely on a version skew (an archive from a newer `pg_dump` starts
    * with `SET transaction_timeout = 0`, which Postgres < 17 rejects) while the data restores perfectly, so a run that
    * reached that summary succeeded with warnings.
    */
  def restoreOutcome(exit: ExitCode, output: List[ToolOutput]): Either[Problem, List[Outcome]] =
    val notable = output
      .map((_).value.trim)
      .filter(_.nonEmpty)
      .filter(l => l.toLowerCase.contains("error") || l.toLowerCase.contains("warning"))
    val completed = output.exists(l => IgnoredErrors.findFirstMatchIn((l).value).isDefined)
    if exit == ExitCode(0) || completed then Right((notable).map(refactorValue9 => Outcome(refactorValue9)))
    else
      Left(Problem.Unclassified(Legacy.Text(s"pg_restore failed (exit $exit): ${notable.takeRight(5).mkString("; ")}")))

  val CodeMaintenance = "maintenance"

  def maintenance(op: String): Api.Error =
    Api.Error(
      Problem.Unclassified(Legacy.Text(s"$op is unavailable: a restore is in progress on this server")),
      Some(Api.Error.Code(CodeMaintenance))
    )

  def missing(file: Result.File): Api.Error =
    Api.Error(
      Problem.Unclassified(Legacy.Text(s"no backup named '$file' in the backups directory")),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  /** Formatted against `Locale.ROOT`: the string goes onto the wire, and JDK 25's CLDR renders `%.1f` as `2,5` under
    * locales such as `en_PL`.
    */
  def humanBytes(n: ByteCount): String =
    val units = Vector("kB", "MB", "GB", "TB")
    if (n).value < 1000 then (s"$n B")
    else
      val exp = math.min(units.size, (math.log((n).value.toDouble) / math.log(1000)).toInt)
      val v = (n).value.toDouble / math.pow(1000, exp.toDouble)
      (String.format(Locale.ROOT, "%.1f %s", v, units(exp - 1)))

  final case class Info(file: Info.File, bytes: ByteCount, createdAt: Info.CreatedAt)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Info:
    export Result.File
    type CreatedAt = CreatedAt.T
    object CreatedAt extends Newtype[String, Pure]

  final case class Result(
      file: Result.File,
      bytes: ByteCount,
      sha256: Sha256,
      durationMs: Result.DurationMs,
      pruned: List[Result.Pruned] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Result:
    type File = File.T
    object File extends Newtype[String, Pure]
    type DurationMs = DurationMs.T
    object DurationMs extends Newtype[Long, Pure]
    type Pruned = Pruned.T
    object Pruned extends Newtype[String, Pure]

  enum State derives CanEqual:
    case Running, Done, Failed
    def wire: State.Wire = State.Wire(toString.toLowerCase(java.util.Locale.ROOT))

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[State] = values.find(_.wire == s)

  final case class Status(
      file: Status.File,
      state: State.Wire,
      durationMs: Status.DurationMs,
      bytes: Option[ByteCount] = None,
      sha256: Option[Sha256] = None,
      error: Option[Problem] = None,
      pruned: List[Status.Pruned] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def stateOf: Option[State] = State.parse(state)
    def result: Option[Result] =
      for b <- bytes; h <- sha256 if stateOf.contains(State.Done) yield Result(file, b, h, durationMs, pruned)

  object Status:

    export Result.{DurationMs, File, File as FailedFile, File as RunningFile}
    export Result.Pruned

    def running(file: Status.RunningFile, elapsedMs: Result.DurationMs): Status =
      Status(file, State.Running.wire, Result.DurationMs((elapsedMs).value))
    def done(r: Result): Status =
      Status(r.file, State.Done.wire, r.durationMs, Some(r.bytes), Some(r.sha256), pruned = r.pruned)
    def failed(file: Status.FailedFile, elapsedMs: Result.DurationMs, error: Problem): Status =
      Status(file, State.Failed.wire, Result.DurationMs((elapsedMs).value), error = Some(error))
