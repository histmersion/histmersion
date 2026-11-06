package com.histmersion.api.maintenance

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Plural
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.TableSize
import com.histmersion.api.backup.BackupsUsage
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
object Storage:
  type Depth = Depth.T
  object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Hint = Hint.T
  object Hint extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val DefaultLowFreeBytes: com.histmersion.api.ByteCount = com.histmersion.api.ByteCount(10L * 1000 * 1000 * 1000)

  def under(child: com.histmersion.api.maintenance.Storage.Entry.Path, parent: com.histmersion.api.maintenance.Storage.Entry.Path): Boolean =
    def norm(p: com.histmersion.api.maintenance.Storage.Entry.Path) = (p).value.replace('\\', '/').stripSuffix("/")
    val c = norm(child)
    val p = norm(parent)
    c == p || c.startsWith(p + "/")

  def nest(entries: List[Storage.Entry]): List[Storage.Entry] =
    val withPath = entries.zipWithIndex.collect { case (e, i) if e.path.isDefined => (e, i) }
    entries.zipWithIndex.map { case (e, i) =>
      if e.within.isDefined then e
      else
        e.path.fold(e) { p =>
          val parents = withPath.filter { case (o, j) =>
            j != i && o.host == e.host && o.path.exists(op => under(p, op) && (op != p || j < i))
          }
          parents
            .maxByOption { case (o, _) => o.path.fold(0)(_.value.length) }
            .fold(e)((o, _) => e.copy(within = Some(o.key)))
        }
    }

  def total(entries: List[Storage.Entry]): com.histmersion.api.ByteCount =
    com.histmersion.api.ByteCount(entries.filter(_.within.isEmpty).flatMap(_.bytes.map(_.value.toLong)).sum)

  def onVolume(entries: List[Storage.Entry], volume: com.histmersion.api.maintenance.Storage.Volume.Id): List[Storage.Entry] =
    entries
      .filter(e => e.within.isEmpty && e.volume.contains(volume) && e.bytes.map(_.value.toLong).exists(_ > 0))
      .sortBy(e => -e.bytes.map(_.value.toLong).getOrElse(0L))

  def hints(report: Storage.Report): List[com.histmersion.api.maintenance.Storage.Hint] =
    report.volumes.filter(_.freeBytes.value.toLong < report.lowFreeBytes.value.toLong).sortBy(_.freeBytes.value.toLong).map { v =>
      val big = onVolume(report.entries, v.id).filter(_.shrink.isDefined).take(3)
      val head =
        s"Low disk space on ${v.id}: ${Backup.humanBytes(_root_.com.histmersion.api.ByteCount(v.freeBytes.value.toLong))} free of ${Backup.humanBytes(_root_.com.histmersion.api.ByteCount(v.totalBytes.value.toLong))}"
      if big.isEmpty then _root_.com.histmersion.api.maintenance.Storage.Hint(s"$head.")
      else
        _root_.com.histmersion.api.maintenance.Storage.Hint(s"$head. Biggest here: " + big
          .map(e => s"${e.label} ${Backup.humanBytes(e.bytes.getOrElse(com.histmersion.api.ByteCount(0L)))} (${e.shrink.getOrElse("")})")
          .mkString("; ") + ".")
    }

  def addLocal(r: Storage.Report, local: List[Storage.Entry], volumes: List[Storage.Volume]): Storage.Report =
    val known = r.entries.flatMap(_.path).toSet
    val add = local.filterNot(e => e.path.exists(known.contains)).map(_.modify(_.host).setTo(Entry.Host("cli")))
    if add.isEmpty then r
    else
      withHints(
        r.modify(
          _.entries).setTo(r.entries ++ nest(add)).modify(_.volumes).setTo((r.volumes ++ volumes.filterNot(v => r.volumes.exists(_.id == v.id)))
        )
      )

  def withHints(r: Storage.Report): Storage.Report = r.modify(_.hints).setTo(hints(r))

  def details(r: Storage.Report, e: Storage.Entry): List[com.histmersion.api.maintenance.Storage.Entry.Detail] =
    val files = e.files.map(_.value.toLong).filter(_ => e.bytes.map(_.value.toLong).isDefined && e.path.isDefined).map(n => Entry.Detail(Plural.count(_root_.com.histmersion.api.Plural.Count(n), _root_.com.histmersion.api.Plural.Noun.Singular("file")).value)).toList
    val extra = e.key.value match
      case "backups" =>
        r.backups
          .map(b =>
            Entry.Detail(Plural.count(_root_.com.histmersion.api.Plural.Count(b.count.value.toLong), _root_.com.histmersion.api.Plural.Noun.Singular("archive")).value + b.newest.fold("")(n => s", newest $n") +
              s", keeps ${if b.keep.value > 0 then b.keep.toString else "every one"}")
          )
          .toList
      case "database" =>
        Option
          .when(r.tables.nonEmpty)(
            Entry.Detail("largest tables: " + r.tables.map(t => s"${t.name} ${Backup.humanBytes(t.bytes)}").mkString(", "))
          )
          .toList
      case _ => Nil
    files ++ extra ++ e.detail.toList

  def shown(entries: List[Storage.Entry]): List[(Storage.Entry, com.histmersion.api.maintenance.Storage.Depth)] =
    val present = entries.filter((refactorCallback => ((refactorCallback.exists) == _root_.com.histmersion.api.maintenance.Storage.Entry.Exists(true))))
    val keys = present.map(_.key).toSet
    def walk(e: Storage.Entry, depth: com.histmersion.api.maintenance.Storage.Depth, seen: Set[com.histmersion.api.maintenance.Storage.Entry.Key]): List[(Storage.Entry, com.histmersion.api.maintenance.Storage.Depth)] =
      (e, depth) :: present
        .filter(c => c.within.contains(e.key) && !seen.contains(c.key))
        .flatMap(c => walk(c, _root_.com.histmersion.api.maintenance.Storage.Depth((depth).value + 1), seen + e.key))
    present.filter(e => e.within.forall(w => !keys.contains(w))).flatMap(walk(_, _root_.com.histmersion.api.maintenance.Storage.Depth(0), Set.empty))

  def size(e: Storage.Entry): String =
    e.bytes.map(_.value.toLong) match
      case None                 => if ((e.exists) == _root_.com.histmersion.api.maintenance.Storage.Entry.Exists(true)) then ("—") else ("not there")
      case Some(b) if ((e.partial) == _root_.com.histmersion.api.maintenance.Storage.Entry.Partial(true)) => (s"at least ${Backup.humanBytes(_root_.com.histmersion.api.ByteCount(b))}")
      case Some(b)              => Backup.humanBytes(_root_.com.histmersion.api.ByteCount(b))

  object Advice:
    type Keep = Keep.T
    object Keep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def shrink(key: com.histmersion.api.maintenance.Storage.Entry.Key, keep: com.histmersion.api.maintenance.Storage.Advice.Keep): Option[com.histmersion.api.maintenance.Storage.Hint] = key.value match
      case "backups" =>
        Some(if (keep).value > 0 then _root_.com.histmersion.api.maintenance.Storage.Hint(s"lower backups.keep (now $keep) — the next backup deletes the older archives")
        else _root_.com.histmersion.api.maintenance.Storage.Hint("set backups.keep (now 0 = keep every archive), or delete old archives"))
      case "basemap" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("histmersion basemap rm (one layer: histmersion basemap rm --layer <name>)"))
      case "gazetteer" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("delete the cached extracts — histmersion places load fetches them again"))
      case "database" =>
        Some(_root_.com.histmersion.api.maintenance.Storage.Hint("histmersion maintenance vacuum; agents.keep-messages-days trims the agents' message traces"))
      case "toolchain" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("histmersion-toolchain remove (needed again only to relink plugins)"))
      case "logs" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("delete old log files (the server reopens them)"))
      case "bundles" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("scratch: safe to empty while no bundle export or import is running"))
      case "acquire-spool" => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("scratch: safe to empty while no acquisition is running"))
      case "codex-home" =>
        Some(
          _root_.com.histmersion.api.maintenance.Storage.Hint("lower agents.providers.codex.keep-sessions-days — older run transcripts are deleted and its SQLite " +
            "files vacuumed while no Codex run uses it (every retention-every-hours); never delete auth.json, its login")
        )
      case k if k.startsWith("cache:") => Some(_root_.com.histmersion.api.maintenance.Storage.Hint("a cache: safe to delete, it is fetched or rebuilt again"))
      case _                           => None

  final case class Entry(
      key: Entry.Key,
      label: Entry.Label,
      group: Entry.Group,
      path: Option[Entry.Path] = None,
      bytes: Option[com.histmersion.api.ByteCount] = None,
      files: Option[com.histmersion.api.maintenance.Storage.FileCount] = None,
      exists: Entry.Exists = _root_.com.histmersion.api.maintenance.Storage.Entry.Exists(true),
      partial: Entry.Partial = _root_.com.histmersion.api.maintenance.Storage.Entry.Partial(false),
      within: Option[Entry.Key] = None,
      detail: Option[Entry.Detail] = None,
      shrink: Option[Entry.Shrink] = None,
      volume: Option[Entry.Volume] = None,
      host: Entry.Host = _root_.com.histmersion.api.maintenance.Storage.Entry.Host("server")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Entry:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Group = Group.T
    object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Path = Path.T
    object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Exists = Exists.T
    object Exists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Partial = Partial.T
    object Partial extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Shrink = Shrink.T
    object Shrink extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Volume = Volume.T
    object Volume extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Host = Host.T
    object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Report(
      measuring: Report.Measuring = _root_.com.histmersion.api.maintenance.Storage.Report.Measuring(false),
      measuredAt: Option[Report.MeasuredAt] = None,
      tookMillis: Option[com.histmersion.api.maintenance.Storage.Report.DurationMillis] = None,
      ageSeconds: Option[com.histmersion.api.maintenance.Storage.Report.AgeSeconds] = None,
      entries: List[Entry] = Nil,
      tables: List[TableSize] = Nil,
      volumes: List[Volume] = Nil,
      backups: Option[BackupsUsage] = None,
      lowFreeBytes: com.histmersion.api.ByteCount = Storage.DefaultLowFreeBytes,
      hints: List[Hint] = Nil,
      problems: List[Report.Problems] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class Volume(id: Volume.Id, totalBytes: com.histmersion.api.ByteCount, freeBytes: com.histmersion.api.ByteCount)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Volume:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FileCount = FileCount.T
  object FileCount extends io.github.iltotore.iron.RefinedType[Long, io.github.iltotore.iron.constraint.any.Pure]

  object Report:
    type Measuring = Measuring.T
    object Measuring extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MeasuredAt = MeasuredAt.T
    object MeasuredAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type AgeSeconds = AgeSeconds.T
    object AgeSeconds extends io.github.iltotore.iron.RefinedType[Long, io.github.iltotore.iron.constraint.any.Pure]

    type DurationMillis = DurationMillis.T
    object DurationMillis extends io.github.iltotore.iron.RefinedType[Long, io.github.iltotore.iron.constraint.any.Pure]

