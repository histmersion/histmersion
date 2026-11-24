package com.histmersion
package api.maintenance

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Plural
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.TableSize
import com.histmersion.api.backup.BackupsUsage
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.ByteCount
import com.histmersion.lint.{LintRule, tolerated}
object Storage:
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]
  type Hint = Hint.T
  object Hint extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val DefaultLowFreeBytes: ByteCount = ByteCount(10L * 1000 * 1000 * 1000)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def under(child: Entry.Path, parent: Entry.Path): Boolean =
    def norm(p: Entry.Path) = (p).value.replace('\\', '/').stripSuffix("/")
    val c = norm(child)
    val p = norm(parent)
    c == p || c.startsWith(p + "/")

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def nest(entries: List[Entry]): List[Entry] =
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def total(entries: List[Entry]): ByteCount =
    ByteCount(entries.filter(_.within.isEmpty).flatMap(_.bytes.map(_.value.toLong)).sum)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  def onVolume(entries: List[Entry], volume: Volume.Id): List[Entry] =
    entries
      .filter(e => e.within.isEmpty && e.volume.contains(volume) && e.bytes.map(_.value.toLong).exists(_ > 0))
      .sortBy(e => -e.bytes.map(_.value.toLong).getOrElse(0L))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hints(report: Report): List[Hint] =
    report.volumes
      .filter(_.freeBytes.value.toLong < report.lowFreeBytes.value.toLong)
      .sortBy(_.freeBytes.value.toLong)
      .map { v =>
        val big = onVolume(report.entries, v.id).filter(_.shrink.isDefined).take(3)
        val head =
          s"Low disk space on ${v.id}: ${Backup.humanBytes(ByteCount(v.freeBytes.value.toLong))} free of ${Backup.humanBytes(ByteCount(v.totalBytes.value.toLong))}"
        if big.isEmpty then Hint(s"$head.")
        else
          Hint(
            s"$head. Biggest here: " + big
              .map(e =>
                s"${e.label} ${Backup.humanBytes(e.bytes.getOrElse(ByteCount(0L)))} (${e.shrink.getOrElse("")})"
              )
              .mkString("; ") + "."
          )
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def addLocal(r: Report, local: List[Entry], volumes: List[Volume]): Report =
    val known = r.entries.flatMap(_.path).toSet
    val add = local.filterNot(e => e.path.exists(known.contains)).map(_.modify(_.host).setTo(Entry.Host("cli")))
    if add.isEmpty then r
    else
      withHints(
        r.modify(_.entries)
          .setTo(r.entries ++ nest(add))
          .modify(_.volumes)
          .setTo((r.volumes ++ volumes.filterNot(v => r.volumes.exists(_.id == v.id))))
      )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def withHints(r: Report): Report = r.modify(_.hints).setTo(hints(r))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def details(r: Report, e: Entry): List[Entry.Detail] =
    val files = e.files
      .map(_.value.toLong)
      .filter(_ => e.bytes.map(_.value.toLong).isDefined && e.path.isDefined)
      .map(n => Entry.Detail(Plural.count(Plural.Count(n), Plural.Noun.Singular("file")).value))
      .toList
    val extra = e.key.value match
      case "backups" =>
        r.backups
          .map(b =>
            Entry.Detail(
              Plural.count(Plural.Count(b.count.value.toLong), Plural.Noun.Singular("archive")).value + b.newest
                .fold("")(n => s", newest $n") +
                s", keeps ${if b.keep.value > 0 then b.keep.toString else "every one"}"
            )
          )
          .toList
      case "database" =>
        Option
          .when(r.tables.nonEmpty)(
            Entry.Detail(
              "largest tables: " + r.tables.map(t => s"${t.name} ${Backup.humanBytes(t.bytes)}").mkString(", ")
            )
          )
          .toList
      case _ => Nil
    files ++ extra ++ e.detail.toList

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def shown(entries: List[Entry]): List[(Entry, Depth)] =
    val present = entries.filter((refactorCallback => ((refactorCallback.exists) == Entry.Exists(true))))
    val keys = present.map(_.key).toSet
    def walk(e: Entry, depth: Depth, seen: Set[Entry.Key]): List[(Entry, Depth)] =
      (e, depth) :: present
        .filter(c => c.within.contains(e.key) && !seen.contains(c.key))
        .flatMap(c => walk(c, Depth((depth).value + 1), seen + e.key))
    present.filter(e => e.within.forall(w => !keys.contains(w))).flatMap(walk(_, Depth(0), Set.empty))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def size(e: Entry): String =
    e.bytes.map(_.value.toLong) match
      case None => if (e.exists) == Entry.Exists(true) then ("—") else ("not there")
      case Some(b) if ((e.partial) == Entry.Partial(true)) => (s"at least ${Backup.humanBytes(ByteCount(b))}")
      case Some(b)                                         => Backup.humanBytes(ByteCount(b))

  object Advice:
    type Keep = Keep.T
    object Keep extends Newtype[Int, Pure]

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def shrink(key: Entry.Key, keep: Keep): Option[Hint] = key.value match
      case "backups" =>
        Some(
          if (keep).value > 0 then Hint(s"lower backups.keep (now $keep) — the next backup deletes the older archives")
          else Hint("set backups.keep (now 0 = keep every archive), or delete old archives")
        )
      case "basemap"   => Some(Hint("histmersion basemap rm (one layer: histmersion basemap rm --layer <name>)"))
      case "gazetteer" => Some(Hint("delete the cached extracts — histmersion places load fetches them again"))
      case "database"  =>
        Some(Hint("histmersion maintenance vacuum; agents.keep-messages-days trims the agents' message traces"))
      case "toolchain"     => Some(Hint("histmersion-toolchain remove (needed again only to relink plugins)"))
      case "logs"          => Some(Hint("delete old log files (the server reopens them)"))
      case "bundles"       => Some(Hint("scratch: safe to empty while no bundle export or import is running"))
      case "acquire-spool" => Some(Hint("scratch: safe to empty while no acquisition is running"))
      case "codex-home"    =>
        Some(
          Hint(
            "lower agents.providers.codex.keep-sessions-days — older run transcripts are deleted and its SQLite " +
              "files vacuumed while no Codex run uses it (every retention-every-hours); never delete auth.json, its login"
          )
        )
      case k if k.startsWith("cache:") => Some(Hint("a cache: safe to delete, it is fetched or rebuilt again"))
      case _                           => None

  final case class Entry(
      key: Entry.Key,
      label: Entry.Label,
      group: Entry.Group,
      path: Option[Entry.Path] = None,
      bytes: Option[ByteCount] = None,
      files: Option[FileCount] = None,
      exists: Entry.Exists = Entry.Exists(true),
      partial: Entry.Partial = Entry.Partial(false),
      within: Option[Entry.Key] = None,
      detail: Option[Entry.Detail] = None,
      shrink: Option[Entry.Shrink] = None,
      volume: Option[Entry.Volume] = None,
      host: Entry.Host = Entry.Host("server")
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Entry:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Group = Group.T
    object Group extends Newtype[String, Pure]
    type Path = Path.T
    object Path extends Newtype[String, Pure]
    type Exists = Exists.T
    object Exists extends Newtype[Boolean, Pure]
    type Partial = Partial.T
    object Partial extends Newtype[Boolean, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]
    type Shrink = Shrink.T
    object Shrink extends Newtype[String, Pure]
    type Volume = Volume.T
    object Volume extends Newtype[String, Pure]
    type Host = Host.T
    object Host extends Newtype[String, Pure]

  final case class Report(
      measuring: Report.Measuring = Report.Measuring(false),
      measuredAt: Option[Report.MeasuredAt] = None,
      tookMillis: Option[Report.DurationMillis] = None,
      ageSeconds: Option[Report.AgeSeconds] = None,
      entries: List[Entry] = Nil,
      tables: List[TableSize] = Nil,
      volumes: List[Volume] = Nil,
      backups: Option[BackupsUsage] = None,
      lowFreeBytes: ByteCount = DefaultLowFreeBytes,
      hints: List[Hint] = Nil,
      problems: List[Report.Problems] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  final case class Volume(id: Volume.Id, totalBytes: ByteCount, freeBytes: ByteCount)
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Volume:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  type FileCount = FileCount.T
  object FileCount extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]

  object Report:
    type Measuring = Measuring.T
    object Measuring extends Newtype[Boolean, Pure]
    type MeasuredAt = MeasuredAt.T
    object MeasuredAt extends Newtype[String, Pure]
    type Problems = Problems.T
    object Problems extends Newtype[String, Pure]

    type AgeSeconds = AgeSeconds.T
    object AgeSeconds extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]

    type DurationMillis = DurationMillis.T
    object DurationMillis extends Newtype[Long, io.github.iltotore.iron.constraint.any.Pure]
