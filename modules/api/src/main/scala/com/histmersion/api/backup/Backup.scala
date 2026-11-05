package com.histmersion.api.backup
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.histmersion.api.Write
import com.histmersion.api.given
import com.histmersion.api.Api
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

object Backup:

  val Prefix = "histmersion-"
  val Extension = ".dump"

  /** No separators, so the names sort chronologically as plain text and are valid on every filesystem. */
  private val Stamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  def timestamp(at: Instant): String = Stamp.format(at.truncatedTo(ChronoUnit.SECONDS))

  def fileName(at: Instant): String = s"$Prefix${timestamp(at)}$Extension"

  def uniqueName(name: String, taken: String => Boolean): String =
    if !taken(name) then name
    else
      val stem = name.dropRight(Extension.length)
      LazyList.from(2).map(n => s"$stem-$n$Extension").find(c => !taken(c)).getOrElse(name)

  private val Written = """histmersion-(\d{8}T\d{6}Z)(?:-(\d+))?\.dump""".r

  /** Only names this service writes count: anything else in the directory is someone's own file. As plain text
    * `…Z-2.dump` sorts before `…Z.dump`, so the order is the stamp, then the same-second suffix.
    */
  def toPrune(names: List[String], keep: Int): List[String] =
    if keep <= 0 then Nil
    else
      val ours = names.collect { case n @ Written(stamp, suffix) =>
        (n, (stamp, Option(suffix).map(_.toInt).getOrElse(1)))
      }
      ours.sortBy(_._2).reverse.drop(keep).map(_._1).reverse

  private val AnyStamp = """(\d{8}T\d{6}Z)(?:-(\d+))?""".r.unanchored

  /** The stamp in the name wins over the mtime: a copied archive keeps its name but not its mtime. */
  def takenAt(name: String, mtime: Instant): Instant = name match
    case AnyStamp(stamp, _) =>
      scala.util.Try(Instant.from(Stamp.parse(stamp))).getOrElse(mtime)
    case _ => mtime

  def newestFirst(bs: List[com.histmersion.api.backup.Backup.Info]): List[com.histmersion.api.backup.Backup.Info] =
    def suffix(n: String) = n match
      case AnyStamp(_, sfx) => Option(sfx).flatMap(_.toIntOption).getOrElse(1)
      case _                => 0
    def at(b: com.histmersion.api.backup.Backup.Info) =
      scala.util.Try(Instant.parse(b.createdAt)).getOrElse(Instant.EPOCH)
    bs.sortBy(b => (at(b), suffix(b.file), b.file)).reverse

  private val Safe = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def safeName(raw: String): Either[String, String] =
    val n = Option(raw).map(_.trim).getOrElse("")
    if n.isEmpty then Left("no backup file given")
    else if n.length > 128 then Left(s"backup file name is too long (${n.length} chars)")
    else if !n.endsWith(Extension) then Left(s"'$n' is not a $Extension file")
    else if n.length == Extension.length then Left(s"'$n' has no name before $Extension")
    else if !Safe.matches(n) then
      Left(s"'$n' is not a plain backup file name — no directories, no '..', letters/digits/._- only")
    else Right(n)

  /** Absent `host`/`port` let libpq decide (its own defaults, `PGHOST`, `PGPORT`). */
  final case class PgTarget(host: Option[PgTarget.Host], port: Option[PgTarget.Port], database: PgTarget.Database):
    /** `-w`: a missing password fails instead of `pg_dump` prompting forever. */
    def args: List[String] =
      host.toList.flatMap(h => List("-h", h)) ++
        port.toList.flatMap(p => List("-p", p.toString)) ++
        List("-d", database, "-w")

  object PgTarget:
    type Host = Host.T
    object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Port = Port.T
    object Port extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Database = Database.T
    object Database extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val JdbcScheme = "jdbc:postgresql:"

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

  /** `pg_restore` exits 1 whenever it skipped any statement, even one it continued past, and then ends with `warning:
    * errors ignored on restore: N`. That happens routinely on a version skew (an archive from a newer `pg_dump` starts
    * with `SET transaction_timeout = 0`, which Postgres < 17 rejects) while the data restores perfectly, so a run that
    * reached that summary succeeded with warnings.
    */
  def restoreOutcome(exit: Int, output: List[String]): Either[String, List[String]] =
    val notable = output
      .map(_.trim)
      .filter(_.nonEmpty)
      .filter(l => l.toLowerCase.contains("error") || l.toLowerCase.contains("warning"))
    val completed = output.exists(l => IgnoredErrors.findFirstMatchIn(l).isDefined)
    if exit == 0 || completed then Right(notable)
    else Left(s"pg_restore failed (exit $exit): ${notable.takeRight(5).mkString("; ")}")

  val CodeMaintenance = "maintenance"

  def maintenance(op: String): Api.Error =
    Api.Error(s"$op is unavailable: a restore is in progress on this server", Some(CodeMaintenance))

  def missing(file: String): Api.Error =
    Api.Error(s"no backup named '$file' in the backups directory", Some(Write.CodeNotFound))

  /** Formatted against `Locale.ROOT`: the string goes onto the wire, and JDK 25's CLDR renders `%.1f` as `2,5` under
    * locales such as `en_PL`.
    */
  def humanBytes(n: Long): String =
    val units = Vector("kB", "MB", "GB", "TB")
    if n < 1000 then s"$n B"
    else
      val exp = math.min(units.size, (math.log(n.toDouble) / math.log(1000)).toInt)
      val v = n.toDouble / math.pow(1000, exp.toDouble)
      String.format(Locale.ROOT, "%.1f %s", v, units(exp - 1))

  final case class Info(file: Info.File, bytes: com.histmersion.api.ByteCount, createdAt: Info.CreatedAt) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CreatedAt = CreatedAt.T
    object CreatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Result(
      file: Result.File,
      bytes: com.histmersion.api.ByteCount,
      sha256: com.histmersion.api.Sha256,
      durationMs: Result.DurationMs,
      pruned: List[Result.Pruned] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Result:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DurationMs = DurationMs.T
    object DurationMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pruned = Pruned.T
    object Pruned extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum State:
    case Running, Done, Failed
    def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s)

  final case class Status(
      file: Status.File,
      state: Status.State,
      durationMs: Status.DurationMs,
      bytes: Option[com.histmersion.api.ByteCount] = None,
      sha256: Option[com.histmersion.api.Sha256] = None,
      error: Option[Status.Error] = None,
      pruned: List[Status.Pruned] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def stateOf: Option[State] = State.parse(state)
    def result: Option[Result] =
      for b <- bytes; h <- sha256 if stateOf.contains(State.Done) yield Result(file, b, h, durationMs, pruned)

  object Status:

    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DurationMs = DurationMs.T
    object DurationMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Error = Error.T
    object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pruned = Pruned.T
    object Pruned extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def running(file: String, elapsedMs: Long): Status = Status(file, State.Running.wire, elapsedMs)
    def done(r: Result): Status =
      Status(r.file, State.Done.wire, r.durationMs, Some(r.bytes), Some(r.sha256), pruned = r.pruned)
    def failed(file: String, elapsedMs: Long, error: String): Status =
      Status(file, State.Failed.wire, elapsedMs, error = Some(error))
