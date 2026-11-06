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

  type NewestFirstSuffixN = NewestFirstSuffixN.T
  object NewestFirstSuffixN extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJdbcPortPart1 = ParseJdbcPortPart1.T
  object ParseJdbcPortPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJdbcPortPart2 = ParseJdbcPortPart2.T
  object ParseJdbcPortPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TimestampResult = TimestampResult.T
  object TimestampResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileNameResult = FileNameResult.T
  object FileNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UniqueNameResult = UniqueNameResult.T
  object UniqueNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UniqueNameName = UniqueNameName.T
  object UniqueNameName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UniqueNameTakenArg1 = UniqueNameTakenArg1.T
  object UniqueNameTakenArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UniqueNameTakenResult = UniqueNameTakenResult.T
  object UniqueNameTakenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPruneResult = ToPruneResult.T
  object ToPruneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPruneNames = ToPruneNames.T
  object ToPruneNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPruneKeep = ToPruneKeep.T
  object ToPruneKeep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TakenAtName = TakenAtName.T
  object TakenAtName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeNameResultPart1 = SafeNameResultPart1.T
  object SafeNameResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeNameResultPart2 = SafeNameResultPart2.T
  object SafeNameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeNameRaw = SafeNameRaw.T
  object SafeNameRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJdbcResultPart1 = ParseJdbcResultPart1.T
  object ParseJdbcResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJdbcUrl = ParseJdbcUrl.T
  object ParseJdbcUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreOutcomeResultPart1 = RestoreOutcomeResultPart1.T
  object RestoreOutcomeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreOutcomeResultPart2 = RestoreOutcomeResultPart2.T
  object RestoreOutcomeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreOutcomeExit = RestoreOutcomeExit.T
  object RestoreOutcomeExit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoreOutcomeOutput = RestoreOutcomeOutput.T
  object RestoreOutcomeOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaintenanceOp = MaintenanceOp.T
  object MaintenanceOp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingFile = MissingFile.T
  object MissingFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HumanBytesResult = HumanBytesResult.T
  object HumanBytesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HumanBytesN = HumanBytesN.T
  object HumanBytesN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Prefix = "histmersion-"
  val Extension = ".dump"

  /** No separators, so the names sort chronologically as plain text and are valid on every filesystem. */
  private val Stamp: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  def timestamp(at: Instant): com.histmersion.api.backup.Backup.TimestampResult = _root_.com.histmersion.api.backup.Backup.TimestampResult(Stamp.format(at.truncatedTo(ChronoUnit.SECONDS)))

  def fileName(at: Instant): com.histmersion.api.backup.Backup.FileNameResult = _root_.com.histmersion.api.backup.Backup.FileNameResult(s"$Prefix${timestamp(at)}$Extension")

  def uniqueName(name: com.histmersion.api.backup.Backup.UniqueNameName, taken: com.histmersion.api.backup.Backup.UniqueNameTakenArg1 => com.histmersion.api.backup.Backup.UniqueNameTakenResult): com.histmersion.api.backup.Backup.UniqueNameResult =
    if !taken(name) then name
    else
      val stem = name.dropRight(Extension.length)
      LazyList.from(2).map(n => s"$stem-$n$Extension").find(c => !taken(c)).getOrElse(name)

  private val Written = """histmersion-(\d{8}T\d{6}Z)(?:-(\d+))?\.dump""".r

  /** Only names this service writes count: anything else in the directory is someone's own file. As plain text
    * `…Z-2.dump` sorts before `…Z.dump`, so the order is the stamp, then the same-second suffix.
    */
  def toPrune(names: List[com.histmersion.api.backup.Backup.ToPruneNames], keep: com.histmersion.api.backup.Backup.ToPruneKeep): List[com.histmersion.api.backup.Backup.ToPruneResult] =
    if keep <= 0 then Nil
    else
      val ours = names.collect { case n @ Written(stamp, suffix) =>
        (n, (stamp, Option(suffix).map(_.toInt).getOrElse(1)))
      }
      ours.sortBy(_._2).reverse.drop(keep).map(_._1).reverse

  private val AnyStamp = """(\d{8}T\d{6}Z)(?:-(\d+))?""".r.unanchored

  /** The stamp in the name wins over the mtime: a copied archive keeps its name but not its mtime. */
  def takenAt(name: com.histmersion.api.backup.Backup.TakenAtName, mtime: Instant): Instant = name match
    case AnyStamp(stamp, _) =>
      scala.util.Try(Instant.from(Stamp.parse(stamp))).getOrElse(mtime)
    case _ => mtime

  def newestFirst(bs: List[com.histmersion.api.backup.Backup.Info]): List[com.histmersion.api.backup.Backup.Info] =
    def suffix(n: com.histmersion.api.backup.Backup.NewestFirstSuffixN) = n match
      case AnyStamp(_, sfx) => Option(sfx).flatMap(_.toIntOption).getOrElse(1)
      case _                => 0
    def at(b: com.histmersion.api.backup.Backup.Info) =
      scala.util.Try(Instant.parse(b.createdAt)).getOrElse(Instant.EPOCH)
    bs.sortBy(b => (at(b), suffix(b.file), b.file)).reverse

  private val Safe = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  def safeName(raw: com.histmersion.api.backup.Backup.SafeNameRaw): Either[com.histmersion.api.backup.Backup.SafeNameResultPart1, com.histmersion.api.backup.Backup.SafeNameResultPart2] =
    val n = Option(raw).map(_.trim).getOrElse("")
    if n.isEmpty then Left(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart1("no backup file given"))
    else if n.length > 128 then Left(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart1(s"backup file name is too long (${n.length} chars)"))
    else if !n.endsWith(Extension) then Left(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart1(s"'$n' is not a $Extension file"))
    else if n.length == Extension.length then Left(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart1(s"'$n' has no name before $Extension"))
    else if !Safe.matches(n) then
      Left(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart1(s"'$n' is not a plain backup file name — no directories, no '..', letters/digits/._- only"))
    else Right(_root_.com.histmersion.api.backup.Backup.SafeNameResultPart2(n))

  /** Absent `host`/`port` let libpq decide (its own defaults, `PGHOST`, `PGPORT`). */
  final case class PgTarget(host: Option[PgTarget.Host], port: Option[PgTarget.Port], database: PgTarget.Database):
    /** `-w`: a missing password fails instead of `pg_dump` prompting forever. */
    def args: List[com.histmersion.api.backup.Backup.PgTarget.ArgsResult] =
      host.toList.flatMap(h => List("-h", h)) ++
        port.toList.flatMap(p => List("-p", p.toString)) ++
        List("-d", database, "-w")

  object PgTarget:
    type ArgsResult = ArgsResult.T
    object ArgsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Host = Host.T
    object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Port = Port.T
    object Port extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Database = Database.T
    object Database extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val JdbcScheme = "jdbc:postgresql:"

  def parseJdbc(url: com.histmersion.api.backup.Backup.ParseJdbcUrl): Either[com.histmersion.api.backup.Backup.ParseJdbcResultPart1, PgTarget] =
    val u = Option(url).map(_.trim).getOrElse("")
    if !u.startsWith(JdbcScheme) then Left(_root_.com.histmersion.api.backup.Backup.ParseJdbcResultPart1(s"not a PostgreSQL JDBC URL: '$u'"))
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
      val port: Either[com.histmersion.api.backup.Backup.ParseJdbcPortPart1, Option[com.histmersion.api.backup.Backup.ParseJdbcPortPart2]] = portText match
        case None    => Right(None)
        case Some(p) => ((p.toIntOption.map(Some(_)).toRight(s"'$p' is not a port number in '$u'")).left.map(refactorError => _root_.com.histmersion.api.backup.Backup.ParseJdbcPortPart1(refactorError))).map(refactorValue2 => (refactorValue2).map(refactorValue3 => _root_.com.histmersion.api.backup.Backup.ParseJdbcPortPart2(refactorValue3)))
      for
        name <- Some(db.trim).filter(_.nonEmpty).toRight(s"JDBC URL has no database name: '$u'")
        p <- port
      yield PgTarget((Some(host).filter(_.nonEmpty)).map(refactorValue10 => _root_.com.histmersion.api.backup.Backup.PgTarget.Host(refactorValue10)), p, _root_.com.histmersion.api.backup.Backup.PgTarget.Database(name))

  private val IgnoredErrors = """errors ignored on restore:\s*(\d+)""".r

  /** `pg_restore` exits 1 whenever it skipped any statement, even one it continued past, and then ends with `warning:
    * errors ignored on restore: N`. That happens routinely on a version skew (an archive from a newer `pg_dump` starts
    * with `SET transaction_timeout = 0`, which Postgres < 17 rejects) while the data restores perfectly, so a run that
    * reached that summary succeeded with warnings.
    */
  def restoreOutcome(exit: com.histmersion.api.backup.Backup.RestoreOutcomeExit, output: List[com.histmersion.api.backup.Backup.RestoreOutcomeOutput]): Either[com.histmersion.api.backup.Backup.RestoreOutcomeResultPart1, List[com.histmersion.api.backup.Backup.RestoreOutcomeResultPart2]] =
    val notable = output
      .map(_.trim)
      .filter(_.nonEmpty)
      .filter(l => l.toLowerCase.contains("error") || l.toLowerCase.contains("warning"))
    val completed = output.exists(l => IgnoredErrors.findFirstMatchIn(l).isDefined)
    if exit == 0 || completed then Right(notable)
    else Left(_root_.com.histmersion.api.backup.Backup.RestoreOutcomeResultPart1(s"pg_restore failed (exit $exit): ${notable.takeRight(5).mkString("; ")}"))

  val CodeMaintenance = "maintenance"

  def maintenance(op: com.histmersion.api.backup.Backup.MaintenanceOp): Api.Error =
    Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"$op is unavailable: a restore is in progress on this server"), Some(_root_.com.histmersion.api.Api.Error.Code(CodeMaintenance)))

  def missing(file: com.histmersion.api.backup.Backup.MissingFile): Api.Error =
    Api.Error(_root_.com.histmersion.api.Api.Error.Message(s"no backup named '$file' in the backups directory"), Some(_root_.com.histmersion.api.Api.Error.Code(Write.CodeNotFound)))

  /** Formatted against `Locale.ROOT`: the string goes onto the wire, and JDK 25's CLDR renders `%.1f` as `2,5` under
    * locales such as `en_PL`.
    */
  def humanBytes(n: com.histmersion.api.backup.Backup.HumanBytesN): com.histmersion.api.backup.Backup.HumanBytesResult =
    val units = Vector("kB", "MB", "GB", "TB")
    if n < 1000 then _root_.com.histmersion.api.backup.Backup.HumanBytesResult(s"$n B")
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
    def wire: com.histmersion.api.backup.Backup.State.WireResult = _root_.com.histmersion.api.backup.Backup.State.WireResult(toString.toLowerCase(java.util.Locale.ROOT))

  object State:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.backup.Backup.State.ParseS): Option[State] = values.find(_.wire == s)

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

    type RunningFile = RunningFile.T
    object RunningFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunningElapsedMs = RunningElapsedMs.T
    object RunningElapsedMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FailedFile = FailedFile.T
    object FailedFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FailedElapsedMs = FailedElapsedMs.T
    object FailedElapsedMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FailedError = FailedError.T
    object FailedError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    def running(file: com.histmersion.api.backup.Backup.Status.RunningFile, elapsedMs: com.histmersion.api.backup.Backup.Status.RunningElapsedMs): Status = Status(file, State.Running.wire, elapsedMs)
    def done(r: Result): Status =
      Status(r.file, State.Done.wire, r.durationMs, Some(r.bytes), Some(r.sha256), pruned = r.pruned)
    def failed(file: com.histmersion.api.backup.Backup.Status.FailedFile, elapsedMs: com.histmersion.api.backup.Backup.Status.FailedElapsedMs, error: com.histmersion.api.backup.Backup.Status.FailedError): Status =
      Status(file, State.Failed.wire, elapsedMs, error = Some(error))
