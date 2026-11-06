package com.histmersion.api

import com.histmersion.api.backup.{Backup, RestoreResult}
import com.histmersion.api.library.Label
object Markdown:
  type CharLimit = CharLimit.T
  object CharLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PeriodStart = _root_.com.histmersion.api.Markdown.PeriodLabelStart
  val PeriodStart: _root_.com.histmersion.api.Markdown.PeriodLabelStart.type = _root_.com.histmersion.api.Markdown.PeriodLabelStart
  type PeriodEnd = _root_.com.histmersion.api.Markdown.PeriodLabelEnd
  val PeriodEnd: _root_.com.histmersion.api.Markdown.PeriodLabelEnd.type = _root_.com.histmersion.api.Markdown.PeriodLabelEnd
  type PeriodLabelStart = _root_.com.histmersion.api.library.Bucket.Start
  val PeriodLabelStart: _root_.com.histmersion.api.library.Bucket.Start.type = _root_.com.histmersion.api.library.Bucket.Start
  type PeriodLabelEnd = _root_.com.histmersion.api.library.Bucket.End
  val PeriodLabelEnd: _root_.com.histmersion.api.library.Bucket.End.type = _root_.com.histmersion.api.library.Bucket.End
  type AsOfNoteAsOf = _root_.com.histmersion.api.library.TimeHistogram.AsOf
  val AsOfNoteAsOf: _root_.com.histmersion.api.library.TimeHistogram.AsOf.type = _root_.com.histmersion.api.library.TimeHistogram.AsOf

  private[api] def year(y: Option[Int]): String = (y.map(_.toString).getOrElse("n.d."))
  private[api] def fmtYear(v: com.histmersion.api.library.Tree.Year): String = YearFmt(v)
  private[api] def period(start: Option[com.histmersion.api.Markdown.PeriodStart], end: Option[com.histmersion.api.Markdown.PeriodEnd]): String = ((periodLabel(start, end)).value)

  def periodLabel(start: Option[com.histmersion.api.Markdown.PeriodLabelStart], end: Option[com.histmersion.api.Markdown.PeriodLabelEnd]): String = YearFmt.period(start, end)
  private[api] def place(region: Option[com.histmersion.api.Place.Region], lat: Option[com.histmersion.api.Geo.Latitude], lon: Option[com.histmersion.api.Geo.Longitude]): String =
    (lat, lon) match
      case (Some(la), Some(lo)) => (s"${region.map((_).value + " ").getOrElse("")}($la, $lo)")
      case _                    => region.getOrElse("")

  def pagingFooter(p: OffsetPage): String = OffsetPage.footer(p)

  def chipLinks(values: String, hashKey: String): String =
    chipLinks((values).value.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList, hashKey)

  def chipLinks(values: List[String], hashKey: String): String =
    values
      .filter((_).value.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${enc(((v).value))})")
      .mkString(", ")

  private[api] def enc(s: String): String = (java.net.URLEncoder.encode((s).value, "UTF-8").replace("+", "%20"))

  def bodySlice(text: com.histmersion.api.Source.Body, offset: com.histmersion.api.personal.UnreadPage.Offset, total: Int): String =
    val remaining = (total).value - (offset).value - (text).value.length
    if remaining > 0 then (s"$text\n\n_…$remaining more chars — pass offset=${(offset).value + (text).value.length}_")
    else ((text).value)

  def backup(r: Backup.Result): com.histmersion.api.Markdown.Text =
    s"""### Backup written
       |
       |- **file:** ${r.file}
       |- **size:** ${Backup.humanBytes(r.bytes)}
       |- **sha256:** `${r.sha256}`
       |- **took:** ${r.durationMs} ms""".stripMargin +
      (if r.pruned.isEmpty then ""
       else s"\n- **pruned** (older than the newest kept): ${r.pruned.mkString(", ")}")

  def backupStatus(s: Backup.Status): com.histmersion.api.Markdown.Text =
    s.stateOf match
      case Some(Backup.State.Done)   => s.result.map(backup).getOrElse(s"### Backup written\n\n- **file:** ${s.file}")
      case Some(Backup.State.Failed) =>
        _root_.com.histmersion.api.Markdown.Text(s"### Backup failed\n\n- **file:** ${s.file}\n- **error:** ${s.error.getOrElse("unknown")}")
      case _ =>
        _root_.com.histmersion.api.Markdown.Text(s"### Backup running\n\n- **file:** ${s.file}\n- **for:** ${(s.durationMs).value / 1000} s — " +
          "`histmersion backup` waits for it; `GET /api/backup/status` reports when it is written")

  def backups(rows: Vector[Backup.Info]): com.histmersion.api.Markdown.Text =
    if rows.isEmpty then _root_.com.histmersion.api.Markdown.Text("_No backups on the server yet._")
    else
      val items = rows.map(b => s"- **${b.file}** — ${Backup.humanBytes(b.bytes)} · ${b.createdAt}")
      _root_.com.histmersion.api.Markdown.Text(s"### ${rows.size} backup(s)\n\n${items.mkString("\n")}")

  def restored(r: RestoreResult): com.histmersion.api.Markdown.Text =
    val warn =
      if r.warnings.isEmpty then ""
      else
        s"\n\n_pg_restore reported ${r.warnings.size} ignored error(s):_\n" +
          r.warnings.take(10).map(w => s"- $w").mkString("\n")
    _root_.com.histmersion.api.Markdown.Text(s"""### Restored ${r.file}
       |
       |- **schema version:** ${r.schemaVersion} (Flyway validate passed)
       |- **sources:** ${r.sourcesBefore} → ${r.sourcesAfter}
       |- **took:** ${r.durationMs} ms""".stripMargin + warn)

  def contestedMark(contested: Boolean, label: String = ("")): String =
    if ((contested) == (true)) && !(label).value.toLowerCase.contains("contested") then (" (contested)") else ("")

  private[api] def asOfNote(asOf: Option[com.histmersion.api.Markdown.AsOfNoteAsOf]): String =
    asOf.map(y => s" · as of ${fmtYear(y)}").getOrElse("")
