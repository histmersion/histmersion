package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The Storage part of `GET /api/about` (`histmersion about`, the web About window): every place
  * Histmersion keeps data on disk — its path, its size, the free space on its volume — and what to
  * do when a disk runs low. The server measures its own locations in the background
  * (`server/StorageProbe`, `storage.*` settings) and answers the last measurement; the CLI adds the
  * locations on its own machine when it talks to a server on the same one. */

/** One place on disk (or, for the database, inside Postgres).
  *
  * @param key     stable id (`database`, `backups`, `basemap`, …); [[StorageAdvice]] keys on it
  * @param label   what a person calls it
  * @param group   `database` · `backups` · `corpus` · `caches` · `install` · `logs` · `other`
  * @param path    where it is (`None`: the database's own size, which lives inside the cluster)
  * @param bytes   its size; `None` = nothing there (or not measured)
  * @param files   how many regular files the walk counted
  * @param exists  the path is there
  * @param partial a cap (time or entries, `storage.max-*`) stopped the walk: `bytes` is a lower bound
  * @param within  the key of the entry this one is part of — counted there, not again in the total
  * @param detail  what is in it (`3 archives, newest …`, the base-map files, …)
  * @param shrink  how to make it smaller, when that is up to the person
  * @param volume  the id of the [[StorageVolume]] it is on
  * @param host    `server`, or `cli` for a location on the machine the CLI runs on */
final case class StorageEntry(
    key: String,
    label: String,
    group: String,
    path: Option[String] = None,
    bytes: Option[Long] = None,
    files: Option[Long] = None,
    exists: Boolean = true,
    partial: Boolean = false,
    within: Option[String] = None,
    detail: Option[String] = None,
    shrink: Option[String] = None,
    volume: Option[String] = None,
    host: String = "server"
) derives KindlingsCodecAsObject
object StorageEntry:
  given Schema[StorageEntry] = KindlingsSchema.derived[StorageEntry].schema

/** One of the database's largest tables, indexes and TOAST included (`pg_total_relation_size`). */
final case class TableSize(name: String, bytes: Long) derives KindlingsCodecAsObject
object TableSize:
  given Schema[TableSize] = KindlingsSchema.derived[TableSize].schema

/** A file system the entries sit on, and its free space (what this process may still write). */
final case class StorageVolume(id: String, totalBytes: Long, freeBytes: Long) derives KindlingsCodecAsObject
object StorageVolume:
  given Schema[StorageVolume] = KindlingsSchema.derived[StorageVolume].schema

/** The archives in `backups.dir` and how many `backups.keep` leaves after each dump (0 = every one). */
final case class BackupsUsage(count: Int, bytes: Long, newest: Option[String], keep: Int) derives KindlingsCodecAsObject
object BackupsUsage:
  given Schema[BackupsUsage] = KindlingsSchema.derived[BackupsUsage].schema

/** @param measuring  a measurement is under way; the rest is the previous one (or empty, on the first)
  * @param measuredAt when the entries were measured (ISO-8601)
  * @param tookMillis how long that took
  * @param ageSeconds seconds between `measuredAt` and the server's clock when it answered
  * @param lowFreeBytes below this much free space a volume gets a [[hints]] line (`storage.low-free-gb`)
  * @param hints      what to do about low free space — [[Storage.hints]] of the rest
  * @param problems   what could not be measured — said, never hidden */
final case class StorageReport(
    measuring: Boolean = false,
    measuredAt: Option[String] = None,
    tookMillis: Option[Long] = None,
    ageSeconds: Option[Long] = None,
    entries: List[StorageEntry] = Nil,
    tables: List[TableSize] = Nil,
    volumes: List[StorageVolume] = Nil,
    backups: Option[BackupsUsage] = None,
    lowFreeBytes: Long = Storage.DefaultLowFreeBytes,
    hints: List[String] = Nil,
    problems: List[String] = Nil
) derives KindlingsCodecAsObject
object StorageReport:
  given Schema[StorageReport] = KindlingsSchema.derived[StorageReport].schema

/** How to make each kind of location smaller — one sentence a person can act on. Pure. */
object StorageAdvice:

  def shrink(key: String, keep: Int): Option[String] = key match
    case "backups"          =>
      Some(if keep > 0 then s"lower backups.keep (now $keep) — the next backup deletes the older archives"
           else "set backups.keep (now 0 = keep every archive), or delete old archives")
    case "basemap"          => Some("histmersion basemap rm (one layer: histmersion basemap rm --layer <name>)")
    case "gazetteer"        => Some("delete the cached extracts — histmersion places load fetches them again")
    case "database"         =>
      Some("histmersion maintenance vacuum; agents.keep-messages-days trims the agents' message traces")
    case "toolchain"        => Some("histmersion-toolchain remove (needed again only to relink plugins)")
    case "logs"             => Some("delete old log files (the server reopens them)")
    case "bundles"          => Some("scratch: safe to empty while no bundle export or import is running")
    case "acquire-spool"    => Some("scratch: safe to empty while no acquisition is running")
    case "codex-home"       =>
      Some("lower agents.providers.codex.keep-sessions-days — older run transcripts are deleted and its SQLite " +
        "files vacuumed while no Codex run uses it (every retention-every-hours); never delete auth.json, its login")
    case k if k.startsWith("cache:") => Some("a cache: safe to delete, it is fetched or rebuilt again")
    case _                  => None

/** The arithmetic of a [[StorageReport]]: nesting, totals, free-space hints. Pure. */
object Storage:

  val DefaultLowFreeBytes: Long = 10L * 1000 * 1000 * 1000

  /** `child` is `parent` or a path under it (string paths, either separator). */
  def under(child: String, parent: String): Boolean =
    def norm(p: String) = p.replace('\\', '/').stripSuffix("/")
    val c = norm(child)
    val p = norm(parent)
    c == p || c.startsWith(p + "/")

  /** Each entry whose path lies inside another entry's path gets that entry as `within` (the
    * innermost one, so sizes are not added twice); an entry already placed keeps its `within`. Two
    * entries with the same path: the later one is within the earlier. */
  def nest(entries: List[StorageEntry]): List[StorageEntry] =
    val withPath = entries.zipWithIndex.collect { case (e, i) if e.path.isDefined => (e, i) }
    entries.zipWithIndex.map { case (e, i) =>
      if e.within.isDefined then e
      else e.path.fold(e) { p =>
        val parents = withPath.filter { case (o, j) =>
          j != i && o.host == e.host && o.path.exists(op => under(p, op) && (op != p || j < i))
        }
        // the innermost: the longest containing path
        parents.maxByOption { case (o, _) => o.path.fold(0)(_.length) }.fold(e)((o, _) => e.copy(within = Some(o.key)))
      }
    }

  /** The bytes on disk, each counted once: the entries that are part of no other. */
  def total(entries: List[StorageEntry]): Long =
    entries.filter(_.within.isEmpty).flatMap(_.bytes).sum

  /** The top-level entries on `volume`, largest first. */
  def onVolume(entries: List[StorageEntry], volume: String): List[StorageEntry] =
    entries.filter(e => e.within.isEmpty && e.volume.contains(volume) && e.bytes.exists(_ > 0))
      .sortBy(e => -e.bytes.getOrElse(0L))

  /** One line per volume with less than `report.lowFreeBytes` free: how much is left, and the three
    * biggest consumers on it that can be made smaller, each with how. */
  def hints(report: StorageReport): List[String] =
    report.volumes.filter(_.freeBytes < report.lowFreeBytes).sortBy(_.freeBytes).map { v =>
      val big = onVolume(report.entries, v.id).filter(_.shrink.isDefined).take(3)
      val head = s"Low disk space on ${v.id}: ${Backup.humanBytes(v.freeBytes)} free of ${Backup.humanBytes(v.totalBytes)}"
      if big.isEmpty then s"$head."
      else s"$head. Biggest here: " + big.map(e =>
        s"${e.label} ${Backup.humanBytes(e.bytes.getOrElse(0L))} (${e.shrink.getOrElse("")})").mkString("; ") + "."
    }

  /** The CLI's own locations (`host = cli`) added to the server's report: a location the server
    * already reports (the same path) is left out, volumes are merged by id, and the hints are worked
    * out again over both. */
  def addLocal(r: StorageReport, local: List[StorageEntry], volumes: List[StorageVolume]): StorageReport =
    val known = r.entries.flatMap(_.path).toSet
    val add   = local.filterNot(e => e.path.exists(known.contains)).map(_.copy(host = "cli"))
    if add.isEmpty then r
    else withHints(r.copy(entries = r.entries ++ nest(add),
      volumes = (r.volumes ++ volumes.filterNot(v => r.volumes.exists(_.id == v.id)))))

  /** The report with its hints worked out again (after the CLI added its own entries). */
  def withHints(r: StorageReport): StorageReport = r.copy(hints = hints(r))

  /** What else there is to say about `e`, after its size and before its path: the files counted, the
    * backups' count and newest archive, the database's largest tables, its own detail, what it is
    * part of. The Markdown and the web window both show these. */
  def details(r: StorageReport, e: StorageEntry): List[String] =
    val files = e.files.filter(_ => e.bytes.isDefined && e.path.isDefined).map(n => Plural.count(n, "file")).toList
    val extra = e.key match
      case "backups"  => r.backups.map(b =>
        Plural.count(b.count, "archive") + b.newest.fold("")(n => s", newest $n") +
          s", keeps ${if b.keep > 0 then b.keep.toString else "every one"}").toList
      case "database" => Option.when(r.tables.nonEmpty)(
        "largest tables: " + r.tables.map(t => s"${t.name} ${Backup.humanBytes(t.bytes)}").mkString(", ")).toList
      case _ => Nil
    files ++ extra ++ e.detail.toList

  /** The entries to show, each followed by the ones inside it (depth 0, 1, …), in the report's order;
    * a location that is not there is left out (the JSON keeps it). */
  def shown(entries: List[StorageEntry]): List[(StorageEntry, Int)] =
    val present = entries.filter(_.exists)
    val keys    = present.map(_.key).toSet
    def walk(e: StorageEntry, depth: Int, seen: Set[String]): List[(StorageEntry, Int)] =
      (e, depth) :: present.filter(c => c.within.contains(e.key) && !seen.contains(c.key))
        .flatMap(c => walk(c, depth + 1, seen + e.key))
    present.filter(e => e.within.forall(w => !keys.contains(w))).flatMap(walk(_, 0, Set.empty))

  /** `12.3 GB`, `at least 12.3 GB` (a capped walk), `—` (nothing there). */
  def size(e: StorageEntry): String =
    e.bytes match
      case None                => if e.exists then "—" else "not there"
      case Some(b) if e.partial => s"at least ${Backup.humanBytes(b)}"
      case Some(b)              => Backup.humanBytes(b)

/** The Storage section of `histmersion about`. Pure. */
object StorageMarkdown:

  def render(r: StorageReport): List[String] =
    if r.entries.isEmpty && !r.measuring && r.problems.isEmpty then Nil
    else
      val when = (r.ageSeconds.map(s => s"measured ${AboutMarkdown.age(s)} ago") ++
        r.tookMillis.map(ms => s"took ${ms} ms") ++ Option.when(r.measuring)("measuring…")).mkString(", ")
      val head = s"## Storage" + (if r.entries.nonEmpty then s" — ${Backup.humanBytes(Storage.total(r.entries))} in all" else "") +
        (if when.nonEmpty then s" ($when)" else "")
      val entries = Storage.shown(r.entries).map((e, depth) => entry(r, e, depth))
      val vols = r.volumes.map(v =>
        s"- free space: ${v.id}: ${Backup.humanBytes(v.freeBytes)} free of ${Backup.humanBytes(v.totalBytes)}" +
          (if v.freeBytes < r.lowFreeBytes then " — LOW" else ""))
      val hints = r.hints.map(h => s"- $h")
      val problems = r.problems.map(p => s"- problem: $p")
      List("", head, "") ++ (if r.entries.isEmpty && r.measuring then List("- measuring…") else entries) ++
        vols ++ (if hints.isEmpty then Nil else "" :: hints) ++ problems

  private def entry(r: StorageReport, e: StorageEntry, depth: Int): String =
    val indent = "  " * depth
    val host   = if e.host == "server" then "" else " [on this machine]"
    s"$indent- ${e.label}$host: ${(Storage.size(e) :: Storage.details(r, e) ++ e.path.toList).mkString(" · ")}"
