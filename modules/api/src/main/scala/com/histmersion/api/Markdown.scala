package com.histmersion
package api

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.api.backup.{Backup, RestoreResult}
import com.histmersion.api.library.Label
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.library.Source
import com.histmersion.api.personal.UnreadPage
object Markdown:
  type CharLimit = CharLimit.T
  object CharLimit extends Newtype[Int, Pure]

  private[api] def year(y: Option[Int]): String = (y.map(_.toString).getOrElse("n.d."))
  private[api] def fmtYear(v: Year): String = YearFmt(v)
  private[api] def period(start: Option[Year], end: Option[Year]): String = ((periodLabel(start, end)))

  def periodLabel(start: Option[Year], end: Option[Year]): String = YearFmt.period(start, end)
  private[api] def place(region: Option[Place.Region], lat: Option[Latitude], lon: Option[Longitude]): String =
    (lat, lon) match
      case (Some(la), Some(lo)) => (s"${region.map((_).value + " ").getOrElse("")}($la, $lo)")
      case _                    => region.map(_.value).getOrElse("")

  def pagingFooter(p: OffsetPage): String = OffsetPage.footer(p)

  def chipLinks(values: String, hashKey: String): String =
    chipLinks((values).split(",").iterator.map(_.trim).filter(_.nonEmpty).toList, hashKey)

  def chipLinks(values: List[String], hashKey: String): String =
    values
      .filter((_).nonEmpty)
      .map(v =>
        s"[${Label.display(Label.Text(v)).value.replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${enc(((v)))})"
      )
      .mkString(", ")

  private[api] def enc(s: String): String = (java.net.URLEncoder.encode((s), "UTF-8").replace("+", "%20"))

  def bodySlice(text: Source.Body, offset: UnreadPage.Offset, total: Int): String =
    val remaining = (total) - (offset).value - (text).value.length
    if remaining > 0 then (s"$text\n\n_…$remaining more chars — pass offset=${(offset).value + (text).value.length}_")
    else ((text).value)

  def backup(r: Backup.Result): String =
    s"""### Backup written
       |
       |- **file:** ${r.file}
       |- **size:** ${Backup.humanBytes(r.bytes)}
       |- **sha256:** `${r.sha256}`
       |- **took:** ${r.durationMs} ms""".stripMargin +
      (if r.pruned.isEmpty then ""
       else s"\n- **pruned** (older than the newest kept): ${r.pruned.mkString(", ")}")

  def backupStatus(s: Backup.Status): String =
    s.stateOf match
      case Some(Backup.State.Done)   => s.result.map(backup).getOrElse(s"### Backup written\n\n- **file:** ${s.file}")
      case Some(Backup.State.Failed) =>
        (s"### Backup failed\n\n- **file:** ${s.file}\n- **error:** ${s.problems.map(_.sentences.mkString("; ")).getOrElse("unknown")}")
      case _ =>
        (s"### Backup running\n\n- **file:** ${s.file}\n- **for:** ${(s.durationMs).value / 1000} s — " +
          "`histmersion backup` waits for it; `GET /api/backup/status` reports when it is written")

  def backups(rows: Vector[Backup.Info]): String =
    if rows.isEmpty then ("_No backups on the server yet._")
    else
      val items = rows.map(b => s"- **${b.file}** — ${Backup.humanBytes(b.bytes)} · ${b.createdAt}")
      (s"### ${rows.size} backup(s)\n\n${items.mkString("\n")}")

  def restored(r: RestoreResult): String =
    val warn =
      if r.warnings.isEmpty then ""
      else
        s"\n\n_pg_restore reported ${r.warnings.size} ignored error(s):_\n" +
          r.warnings.take(10).map(w => s"- $w").mkString("\n")
    (s"""### Restored ${r.file}
       |
       |- **schema version:** ${r.schemaVersion} (Flyway validate passed)
       |- **sources:** ${r.sourcesBefore} → ${r.sourcesAfter}
       |- **took:** ${r.durationMs} ms""".stripMargin + warn)

  def contestedMark(contested: Boolean, label: String = ("")): String =
    if ((contested) == (true)) && !(label).toLowerCase.contains("contested") then (" (contested)") else ("")

  private[api] def asOfNote(asOf: Option[Year]): String =
    asOf.map(y => s" · as of ${fmtYear(y)}").getOrElse("")
