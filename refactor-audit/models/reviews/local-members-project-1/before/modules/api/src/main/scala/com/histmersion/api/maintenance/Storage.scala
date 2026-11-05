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

  type DefaultLowFreeBytes = DefaultLowFreeBytes.T
  object DefaultLowFreeBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnderResult = UnderResult.T
  object UnderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnderChild = UnderChild.T
  object UnderChild extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnderParent = UnderParent.T
  object UnderParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnVolumeVolume = OnVolumeVolume.T
  object OnVolumeVolume extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HintsResult = HintsResult.T
  object HintsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetailsResult = DetailsResult.T
  object DetailsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShownResultPart2 = ShownResultPart2.T
  object ShownResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeResult = SizeResult.T
  object SizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val DefaultLowFreeBytes: com.histmersion.api.maintenance.Storage.DefaultLowFreeBytes = 10L * 1000 * 1000 * 1000

  def under(child: com.histmersion.api.maintenance.Storage.UnderChild, parent: com.histmersion.api.maintenance.Storage.UnderParent): com.histmersion.api.maintenance.Storage.UnderResult =
    def norm(p: String) = p.replace('\\', '/').stripSuffix("/")
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
            .maxByOption { case (o, _) => o.path.fold(0)(_.length) }
            .fold(e)((o, _) => e.copy(within = Some(o.key)))
        }
    }

  def total(entries: List[Storage.Entry]): com.histmersion.api.ByteCount =
    com.histmersion.api.ByteCount(entries.filter(_.within.isEmpty).flatMap(_.bytes.map(_.value.toLong)).sum)

  def onVolume(entries: List[Storage.Entry], volume: com.histmersion.api.maintenance.Storage.OnVolumeVolume): List[Storage.Entry] =
    entries
      .filter(e => e.within.isEmpty && e.volume.contains(volume) && e.bytes.map(_.value.toLong).exists(_ > 0))
      .sortBy(e => -e.bytes.map(_.value.toLong).getOrElse(0L))

  def hints(report: Storage.Report): List[com.histmersion.api.maintenance.Storage.HintsResult] =
    report.volumes.filter(_.freeBytes.value.toLong < report.lowFreeBytes.value.toLong).sortBy(_.freeBytes.value.toLong).map { v =>
      val big = onVolume(report.entries, v.id).filter(_.shrink.isDefined).take(3)
      val head =
        s"Low disk space on ${v.id}: ${Backup.humanBytes(v.freeBytes.value.toLong)} free of ${Backup.humanBytes(v.totalBytes.value.toLong)}"
      if big.isEmpty then s"$head."
      else
        s"$head. Biggest here: " + big
          .map(e => s"${e.label} ${Backup.humanBytes(e.bytes.map(_.value.toLong).getOrElse(0L))} (${e.shrink.getOrElse("")})")
          .mkString("; ") + "."
    }

  def addLocal(r: Storage.Report, local: List[Storage.Entry], volumes: List[Storage.Volume]): Storage.Report =
    val known = r.entries.flatMap(_.path).toSet
    val add = local.filterNot(e => e.path.exists(known.contains)).map(_.modify(_.host).setTo("cli"))
    if add.isEmpty then r
    else
      withHints(
        r.modify(
          _.entries).setTo(r.entries ++ nest(add)).modify(_.volumes).setTo((r.volumes ++ volumes.filterNot(v => r.volumes.exists(_.id == v.id)))
        )
      )

  def withHints(r: Storage.Report): Storage.Report = r.modify(_.hints).setTo(hints(r))

  def details(r: Storage.Report, e: Storage.Entry): List[com.histmersion.api.maintenance.Storage.DetailsResult] =
    val files = e.files.map(_.value.toLong).filter(_ => e.bytes.map(_.value.toLong).isDefined && e.path.isDefined).map(n => Plural.count(n, "file")).toList
    val extra = e.key match
      case "backups" =>
        r.backups
          .map(b =>
            Plural.count(b.count, "archive") + b.newest.fold("")(n => s", newest $n") +
              s", keeps ${if b.keep > 0 then b.keep.toString else "every one"}"
          )
          .toList
      case "database" =>
        Option
          .when(r.tables.nonEmpty)(
            "largest tables: " + r.tables.map(t => s"${t.name} ${Backup.humanBytes(t.bytes)}").mkString(", ")
          )
          .toList
      case _ => Nil
    files ++ extra ++ e.detail.toList

  def shown(entries: List[Storage.Entry]): List[(Storage.Entry, com.histmersion.api.maintenance.Storage.ShownResultPart2)] =
    val present = entries.filter(_.exists)
    val keys = present.map(_.key).toSet
    def walk(e: Storage.Entry, depth: Int, seen: Set[String]): List[(Storage.Entry, Int)] =
      (e, depth) :: present
        .filter(c => c.within.contains(e.key) && !seen.contains(c.key))
        .flatMap(c => walk(c, depth + 1, seen + e.key))
    present.filter(e => e.within.forall(w => !keys.contains(w))).flatMap(walk(_, 0, Set.empty))

  def size(e: Storage.Entry): com.histmersion.api.maintenance.Storage.SizeResult =
    e.bytes.map(_.value.toLong) match
      case None                 => if e.exists then "—" else "not there"
      case Some(b) if e.partial => s"at least ${Backup.humanBytes(b)}"
      case Some(b)              => Backup.humanBytes(b)

  object Advice:

    type ShrinkResult = ShrinkResult.T
    object ShrinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShrinkKey = ShrinkKey.T
    object ShrinkKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShrinkKeep = ShrinkKeep.T
    object ShrinkKeep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def shrink(key: com.histmersion.api.maintenance.Storage.Advice.ShrinkKey, keep: com.histmersion.api.maintenance.Storage.Advice.ShrinkKeep): Option[com.histmersion.api.maintenance.Storage.Advice.ShrinkResult] = key match
      case "backups" =>
        Some(if keep > 0 then s"lower backups.keep (now $keep) — the next backup deletes the older archives"
        else "set backups.keep (now 0 = keep every archive), or delete old archives")
      case "basemap"   => Some("histmersion basemap rm (one layer: histmersion basemap rm --layer <name>)")
      case "gazetteer" => Some("delete the cached extracts — histmersion places load fetches them again")
      case "database"  =>
        Some("histmersion maintenance vacuum; agents.keep-messages-days trims the agents' message traces")
      case "toolchain"     => Some("histmersion-toolchain remove (needed again only to relink plugins)")
      case "logs"          => Some("delete old log files (the server reopens them)")
      case "bundles"       => Some("scratch: safe to empty while no bundle export or import is running")
      case "acquire-spool" => Some("scratch: safe to empty while no acquisition is running")
      case "codex-home"    =>
        Some(
          "lower agents.providers.codex.keep-sessions-days — older run transcripts are deleted and its SQLite " +
            "files vacuumed while no Codex run uses it (every retention-every-hours); never delete auth.json, its login"
        )
      case k if k.startsWith("cache:") => Some("a cache: safe to delete, it is fetched or rebuilt again")
      case _                           => None

  final case class Entry(
      key: Entry.Key,
      label: Entry.Label,
      group: Entry.Group,
      path: Option[Entry.Path] = None,
      bytes: Option[com.histmersion.api.ByteCount] = None,
      files: Option[com.histmersion.api.maintenance.Storage.FileCount] = None,
      exists: Entry.Exists = true,
      partial: Entry.Partial = false,
      within: Option[Entry.Within] = None,
      detail: Option[Entry.Detail] = None,
      shrink: Option[Entry.Shrink] = None,
      volume: Option[Entry.Volume] = None,
      host: Entry.Host = "server"
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
    type Within = Within.T
    object Within extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Shrink = Shrink.T
    object Shrink extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Volume = Volume.T
    object Volume extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Host = Host.T
    object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Report(
      measuring: Report.Measuring = false,
      measuredAt: Option[Report.MeasuredAt] = None,
      tookMillis: Option[com.histmersion.api.maintenance.Storage.Report.DurationMillis] = None,
      ageSeconds: Option[com.histmersion.api.maintenance.Storage.Report.AgeSeconds] = None,
      entries: List[Entry] = Nil,
      tables: List[TableSize] = Nil,
      volumes: List[Volume] = Nil,
      backups: Option[BackupsUsage] = None,
      lowFreeBytes: com.histmersion.api.ByteCount = com.histmersion.api.ByteCount(Storage.DefaultLowFreeBytes),
      hints: List[Report.Hints] = Nil,
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
    type Hints = Hints.T
    object Hints extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type AgeSeconds = AgeSeconds.T
    object AgeSeconds extends io.github.iltotore.iron.RefinedType[Long, io.github.iltotore.iron.constraint.any.Pure]

    type DurationMillis = DurationMillis.T
    object DurationMillis extends io.github.iltotore.iron.RefinedType[Long, io.github.iltotore.iron.constraint.any.Pure]

