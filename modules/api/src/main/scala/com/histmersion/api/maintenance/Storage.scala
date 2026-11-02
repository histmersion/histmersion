package com.histmersion.api.maintenance

import com.histmersion.api.Plural
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
object Storage:

  val DefaultLowFreeBytes: Long = 10L * 1000 * 1000 * 1000

  def under(child: String, parent: String): Boolean =
    def norm(p: String) = p.replace('\\', '/').stripSuffix("/")
    val c = norm(child)
    val p = norm(parent)
    c == p || c.startsWith(p + "/")

  def nest(entries: List[StorageEntry]): List[StorageEntry] =
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

  def total(entries: List[StorageEntry]): Long =
    entries.filter(_.within.isEmpty).flatMap(_.bytes).sum

  def onVolume(entries: List[StorageEntry], volume: String): List[StorageEntry] =
    entries
      .filter(e => e.within.isEmpty && e.volume.contains(volume) && e.bytes.exists(_ > 0))
      .sortBy(e => -e.bytes.getOrElse(0L))

  def hints(report: StorageReport): List[String] =
    report.volumes.filter(_.freeBytes < report.lowFreeBytes).sortBy(_.freeBytes).map { v =>
      val big = onVolume(report.entries, v.id).filter(_.shrink.isDefined).take(3)
      val head =
        s"Low disk space on ${v.id}: ${Backup.humanBytes(v.freeBytes)} free of ${Backup.humanBytes(v.totalBytes)}"
      if big.isEmpty then s"$head."
      else
        s"$head. Biggest here: " + big
          .map(e => s"${e.label} ${Backup.humanBytes(e.bytes.getOrElse(0L))} (${e.shrink.getOrElse("")})")
          .mkString("; ") + "."
    }

  def addLocal(r: StorageReport, local: List[StorageEntry], volumes: List[StorageVolume]): StorageReport =
    val known = r.entries.flatMap(_.path).toSet
    val add = local.filterNot(e => e.path.exists(known.contains)).map(_.copy(host = "cli"))
    if add.isEmpty then r
    else
      withHints(
        r.copy(
          entries = r.entries ++ nest(add),
          volumes = (r.volumes ++ volumes.filterNot(v => r.volumes.exists(_.id == v.id)))
        )
      )

  def withHints(r: StorageReport): StorageReport = r.copy(hints = hints(r))

  def details(r: StorageReport, e: StorageEntry): List[String] =
    val files = e.files.filter(_ => e.bytes.isDefined && e.path.isDefined).map(n => Plural.count(n, "file")).toList
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

  def shown(entries: List[StorageEntry]): List[(StorageEntry, Int)] =
    val present = entries.filter(_.exists)
    val keys = present.map(_.key).toSet
    def walk(e: StorageEntry, depth: Int, seen: Set[String]): List[(StorageEntry, Int)] =
      (e, depth) :: present
        .filter(c => c.within.contains(e.key) && !seen.contains(c.key))
        .flatMap(c => walk(c, depth + 1, seen + e.key))
    present.filter(e => e.within.forall(w => !keys.contains(w))).flatMap(walk(_, 0, Set.empty))

  def size(e: StorageEntry): String =
    e.bytes match
      case None                 => if e.exists then "—" else "not there"
      case Some(b) if e.partial => s"at least ${Backup.humanBytes(b)}"
      case Some(b)              => Backup.humanBytes(b)
