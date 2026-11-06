package com.histmersion.api

import com.histmersion.api.backup.{Backup, RestoreResult}
import com.histmersion.api.library.Label
object Markdown:

  type YearResult = YearResult.T
  object YearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearY = YearY.T
  object YearY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FmtYearResult = FmtYearResult.T
  object FmtYearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FmtYearV = FmtYearV.T
  object FmtYearV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodResult = PeriodResult.T
  object PeriodResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodStart = PeriodStart.T
  object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodEnd = PeriodEnd.T
  object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodLabelResult = PeriodLabelResult.T
  object PeriodLabelResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodLabelStart = PeriodLabelStart.T
  object PeriodLabelStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodLabelEnd = PeriodLabelEnd.T
  object PeriodLabelEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceResult = PlaceResult.T
  object PlaceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceRegion = PlaceRegion.T
  object PlaceRegion extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLat = PlaceLat.T
  object PlaceLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLon = PlaceLon.T
  object PlaceLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagingFooterResult = PagingFooterResult.T
  object PagingFooterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLinksResult = ChipLinksResult.T
  object ChipLinksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLinksValues = ChipLinksValues.T
  object ChipLinksValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChipLinksHashKey = ChipLinksHashKey.T
  object ChipLinksHashKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncResult = EncResult.T
  object EncResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncS = EncS.T
  object EncS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySliceResult = BodySliceResult.T
  object BodySliceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySliceText = BodySliceText.T
  object BodySliceText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySliceOffset = BodySliceOffset.T
  object BodySliceOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodySliceTotal = BodySliceTotal.T
  object BodySliceTotal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackupResult = BackupResult.T
  object BackupResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackupStatusResult = BackupStatusResult.T
  object BackupStatusResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackupsResult = BackupsResult.T
  object BackupsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestoredResult = RestoredResult.T
  object RestoredResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContestedMarkResult = ContestedMarkResult.T
  object ContestedMarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContestedMarkContested = ContestedMarkContested.T
  object ContestedMarkContested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContestedMarkLabel = ContestedMarkLabel.T
  object ContestedMarkLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsOfNoteResult = AsOfNoteResult.T
  object AsOfNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AsOfNoteAsOf = AsOfNoteAsOf.T
  object AsOfNoteAsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[api] def year(y: Option[com.histmersion.api.Markdown.YearY]): com.histmersion.api.Markdown.YearResult = y.map(_.toString).getOrElse("n.d.")
  private[api] def fmtYear(v: com.histmersion.api.Markdown.FmtYearV): com.histmersion.api.Markdown.FmtYearResult = YearFmt(v)
  private[api] def period(start: Option[com.histmersion.api.Markdown.PeriodStart], end: Option[com.histmersion.api.Markdown.PeriodEnd]): com.histmersion.api.Markdown.PeriodResult = periodLabel(start, end)

  def periodLabel(start: Option[com.histmersion.api.Markdown.PeriodLabelStart], end: Option[com.histmersion.api.Markdown.PeriodLabelEnd]): com.histmersion.api.Markdown.PeriodLabelResult = YearFmt.period(start, end)
  private[api] def place(region: Option[com.histmersion.api.Markdown.PlaceRegion], lat: Option[com.histmersion.api.Markdown.PlaceLat], lon: Option[com.histmersion.api.Markdown.PlaceLon]): com.histmersion.api.Markdown.PlaceResult =
    (lat, lon) match
      case (Some(la), Some(lo)) => s"${region.map(_ + " ").getOrElse("")}($la, $lo)"
      case _                    => region.getOrElse("")

  def pagingFooter(p: OffsetPage): com.histmersion.api.Markdown.PagingFooterResult = OffsetPage.footer(p)

  def chipLinks(values: com.histmersion.api.Markdown.ChipLinksValues, hashKey: com.histmersion.api.Markdown.ChipLinksHashKey): com.histmersion.api.Markdown.ChipLinksResult =
    chipLinks(values.split(",").iterator.map(_.trim).filter(_.nonEmpty).toList, hashKey)

  def chipLinks(values: List[com.histmersion.api.Markdown.ChipLinksValues], hashKey: com.histmersion.api.Markdown.ChipLinksHashKey): com.histmersion.api.Markdown.ChipLinksResult =
    values
      .filter(_.nonEmpty)
      .map(v => s"[${Label.display(v).replace("[", "\\[").replace("]", "\\]")}](/#$hashKey=${enc(v)})")
      .mkString(", ")

  private[api] def enc(s: com.histmersion.api.Markdown.EncS): com.histmersion.api.Markdown.EncResult = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  def bodySlice(text: com.histmersion.api.Markdown.BodySliceText, offset: com.histmersion.api.Markdown.BodySliceOffset, total: com.histmersion.api.Markdown.BodySliceTotal): com.histmersion.api.Markdown.BodySliceResult =
    val remaining = total - offset - text.length
    if remaining > 0 then s"$text\n\n_…$remaining more chars — pass offset=${offset + text.length}_"
    else text

  def backup(r: Backup.Result): com.histmersion.api.Markdown.BackupResult =
    s"""### Backup written
       |
       |- **file:** ${r.file}
       |- **size:** ${Backup.humanBytes(r.bytes)}
       |- **sha256:** `${r.sha256}`
       |- **took:** ${r.durationMs} ms""".stripMargin +
      (if r.pruned.isEmpty then ""
       else s"\n- **pruned** (older than the newest kept): ${r.pruned.mkString(", ")}")

  def backupStatus(s: Backup.Status): com.histmersion.api.Markdown.BackupStatusResult =
    s.stateOf match
      case Some(Backup.State.Done)   => s.result.map(backup).getOrElse(s"### Backup written\n\n- **file:** ${s.file}")
      case Some(Backup.State.Failed) =>
        s"### Backup failed\n\n- **file:** ${s.file}\n- **error:** ${s.error.getOrElse("unknown")}"
      case _ =>
        s"### Backup running\n\n- **file:** ${s.file}\n- **for:** ${s.durationMs / 1000} s — " +
          "`histmersion backup` waits for it; `GET /api/backup/status` reports when it is written"

  def backups(rows: Vector[Backup.Info]): com.histmersion.api.Markdown.BackupsResult =
    if rows.isEmpty then "_No backups on the server yet._"
    else
      val items = rows.map(b => s"- **${b.file}** — ${Backup.humanBytes(b.bytes)} · ${b.createdAt}")
      s"### ${rows.size} backup(s)\n\n${items.mkString("\n")}"

  def restored(r: RestoreResult): com.histmersion.api.Markdown.RestoredResult =
    val warn =
      if r.warnings.isEmpty then ""
      else
        s"\n\n_pg_restore reported ${r.warnings.size} ignored error(s):_\n" +
          r.warnings.take(10).map(w => s"- $w").mkString("\n")
    s"""### Restored ${r.file}
       |
       |- **schema version:** ${r.schemaVersion} (Flyway validate passed)
       |- **sources:** ${r.sourcesBefore} → ${r.sourcesAfter}
       |- **took:** ${r.durationMs} ms""".stripMargin + warn

  def contestedMark(contested: com.histmersion.api.Markdown.ContestedMarkContested, label: com.histmersion.api.Markdown.ContestedMarkLabel = ""): com.histmersion.api.Markdown.ContestedMarkResult =
    if contested && !label.toLowerCase.contains("contested") then " (contested)" else ""

  private[api] def asOfNote(asOf: Option[com.histmersion.api.Markdown.AsOfNoteAsOf]): com.histmersion.api.Markdown.AsOfNoteResult =
    asOf.map(y => s" · as of ${fmtYear(y)}").getOrElse("")
