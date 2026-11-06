package com.histmersion.api.media
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{OffsetPage, Plural}
import com.histmersion.api.Markdown.*

object MediaMarkdown:

  type AssetDetailResult = AssetDetailResult.T
  object AssetDetailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetDetailCk = AssetDetailCk.T
  object AssetDetailCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsLineResult = CountsLineResult.T
  object CountsLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaProvidersResult = MediaProvidersResult.T
  object MediaProvidersResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaImportReportResult = MediaImportReportResult.T
  object MediaImportReportResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaSamplesResult = MediaSamplesResult.T
  object MediaSamplesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaSamplesDryRun = MediaSamplesDryRun.T
  object MediaSamplesDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaCoversReportResult = MediaCoversReportResult.T
  object MediaCoversReportResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaItemLineResult = MediaItemLineResult.T
  object MediaItemLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaListResult = MediaListResult.T
  object MediaListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaItemResult = MediaItemResult.T
  object MediaItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MediaFacetsResult = MediaFacetsResult.T
  object MediaFacetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FigureLicenceFillResult = FigureLicenceFillResult.T
  object FigureLicenceFillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinkLineResult = AssetLinkLineResult.T
  object AssetLinkLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinkListResult = AssetLinkListResult.T
  object AssetLinkListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetLinkResult = AssetLinkResult.T
  object AssetLinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def assetDetail(ck: com.histmersion.api.media.MediaMarkdown.AssetDetailCk, a: AssetDetail): com.histmersion.api.media.MediaMarkdown.AssetDetailResult =
    val loc = (a.lat, a.lon) match
      case (Some(lat), Some(lon)) => s"  \n**Location:** $lat, $lon"
      case _                      => ""
    val per = (a.periodStart, a.periodEnd) match
      case (Some(s), Some(e)) => s"  \n**Period:** ${periodLabel(Some(s), Some(e))}"
      case _                  => ""
    val meta = List(
      a.kind.map(k => s"**Kind:** $k"),
      a.license.map(l => s"**License:** $l"),
      Some(s"**Size:** ${Plural.count(a.bytes, "byte")}"),
      (a.width, a.height) match {
        case (Some(w), Some(h)) => Some(s"**Dimensions:** $w×$h")
        case _                  => None
      }
    ).flatten.mkString("  \n")
    val cap = a.caption.map(c => s"\n\n> $c").getOrElse("")
    _root_.com.histmersion.api.media.MediaMarkdown.AssetDetailResult(s"# `$ck` / `${a.name}`\n\n$meta$loc$per$cap")

  private[api] def countsLine(cs: List[MediaCount]): com.histmersion.api.media.MediaMarkdown.CountsLineResult =
    if cs.isEmpty then _root_.com.histmersion.api.media.MediaMarkdown.CountsLineResult("none")
    else cs.map(c => s"${c.key} ${c.n}${if c.allowed then "" else " (not allowed)"}").mkString(" · ")

  def mediaProviders(ps: List[MediaProvider.Info]): com.histmersion.api.media.MediaMarkdown.MediaProvidersResult =
    if ps.isEmpty then _root_.com.histmersion.api.media.MediaMarkdown.MediaProvidersResult("No media provider is registered (every media plugin is switched off).")
    else
      ps.map { p =>
        val origin = if p.builtin then s"built in, plugin `${p.plugin}`" else s"plugin `${p.plugin}`"
        val terms = if p.licences.trim.isEmpty then "" else s" · licences: ${p.licences}"
        s"- **`${p.name}`** — ${p.title} (${p.over}; $origin$terms)" +
          (if p.description.trim.isEmpty then "" else s"\n  ${p.description.trim}")
      }.mkString("## Media providers\n\n", "\n", "\n\n`histmersion media import <name>` runs one.")

  def mediaImportReport(r: MediaImportReport): com.histmersion.api.media.MediaMarkdown.MediaImportReportResult =
    val mode = if r.dryRun then "dry run — nothing stored" else "stored"
    val licences = if r.byLicence.isEmpty then "_none_"
    else
      r.byLicence
        .map(c => s"| `${c.key}` | ${c.n} | ${if c.allowed then "yes" else "**no**"} |")
        .mkString("| licence | files | allowed |\n|---|---|---|\n", "\n", "")
    val rejected = if r.rejected.isEmpty then ""
    else "\n\n**Not stored**\n" + r.rejected.map(c => s"- ${c.key}: ${c.n}").mkString("\n")
    val done = if r.dryRun then ""
    else
      s"\n\n**Stored:** ${r.stored} new · ${r.updated} refreshed · ${r.linked} link(s) · ${r.categories} Commons ${Plural.noun(r.categories, _root_.com.histmersion.api.Plural.NounSingular("category"), _root_.com.histmersion.api.Plural.NounPlural("categories"))}"
    val revoked = if r.revoked.isEmpty then ""
    else "\n\n**Licence no longer allowed (kept — review):**\n" + r.revoked.map(x => s"- $x").mkString("\n")
    val failed = if r.failed.isEmpty then "" else "\n\n**Failed**\n" + r.failed.map(x => s"- $x").mkString("\n")
    val pending = s"\n\n**Not asked yet:** ${Plural.count(r.pending, _root_.com.histmersion.api.Plural.CountSingular("place/event"), _root_.com.histmersion.api.Plural.CountPlural("places/events"))}" +
      (if r.batches > 0 then s" · ${Plural.count(r.batches, "batch", "batches")} run" else "")
    // assembled line by line, not with stripMargin: the table's rows start with `|` themselves
    List(
      s"# Media import · ${MediaProvider.title(r.provider)} ($mode)",
      "",
      (if MediaProvider.all.contains(r.provider) && r.provider != MediaProvider.Commons then
         s"**Asked about:** ${Plural.count(r.targets, "place")} · ${Plural.count(r.items, "record")} read · " +
           s"${Plural.count(r.statements, "match", "matches")} · ${Plural.count(r.files, "picture")}  "
       else if r.provider == MediaProvider.Pleiades then
         s"**Asked about:** ${Plural.count(r.targets, "place")} · ${Plural.count(r.items, "Pleiades record")} read · " +
           s"${Plural.count(r.statements, "linked Commons file")} · ${Plural.count(r.files, "file")}  "
       else
         s"**Asked about:** ${Plural.count(r.targets, "place/event", "places/events")} · ${Plural.count(r.items, "Wikidata item")} · " +
           s"${Plural.count(r.statements, "picture statement")} · ${Plural.count(r.files, "file")}  "
      ) ,
      s"**By provider:** ${MediaProvider.title(r.provider)} ${r.files}  ",
      s"**By role:** ${countsLine(r.byRole)}",
      "",
      licences + rejected + mediaSamples(r.samples, r.dryRun) + done + revoked + failed + pending
    ).mkString("\n")

  private[api] def mediaSamples(xs: List[MediaSample], dryRun: com.histmersion.api.media.MediaMarkdown.MediaSamplesDryRun): com.histmersion.api.media.MediaMarkdown.MediaSamplesResult =
    if xs.isEmpty then _root_.com.histmersion.api.media.MediaMarkdown.MediaSamplesResult("")
    else
      s"\n\n**${if (dryRun).value then "Would link" else "Linked"} (sample of ${xs.size})**\n" +
        xs.map(x =>
          s"- ${x.targetType} `${x.targetId}` (${x.targetLabel}) ← ${x.title} [`${x.key}`](${x.url}) · `${x.license}` — ${x.basis}"
        ).mkString("\n")

  def mediaCoversReport(r: MediaCoversReport): com.histmersion.api.media.MediaMarkdown.MediaCoversReportResult =
    val mode = if r.dryRun then "dry run — nothing stored" else "stored"
    val verb = if r.dryRun then "would link" else "linked"
    val lines = if r.lines.isEmpty then ""
    else
      "\n\n" + r.lines
        .map(l => s"- [[${l.citekey}]] — ${l.role} from ${MediaProvider.title(l.provider)}: `${l.detail}`")
        .mkString("\n")
    val skipped =
      if r.skipped.isEmpty then "" else "\n\n**None for**\n" + r.skipped.map(c => s"- ${c.key}: ${c.n}").mkString("\n")
    val failed = if r.failed.isEmpty then "" else "\n\n**Failed**\n" + r.failed.map(x => s"- $x").mkString("\n")
    s"# Source pictures ($mode)\n\n${Plural.count(r.sources, _root_.com.histmersion.api.Plural.CountSingular("source"))} looked at · $verb " +
      s"${Plural.count(r.covers, "cover")} and ${Plural.count(r.illustrations, "illustration")}" +
      lines + skipped + failed

  def mediaItemLine(m: MediaItem): com.histmersion.api.media.MediaMarkdown.MediaItemLineResult =
    val origin = m.citekey
      .map(c => s"[[$c]] / `${m.name}`")
      .getOrElse(s"${m.provider.map(MediaProvider.title).getOrElse("?")} `${m.providerKey.getOrElse(m.name)}`")
    val lic = m.license.fold(if m.provider.exists(MediaProvider.covers) then "a cover (no reuse licence; shown locally)"
    else "**no licence**")(l => s"`$l`")
    val targets = if m.targets.isEmpty then ""
    else " → " + m.targets.map(t => s"${t.targetType}:${t.targetId} (${t.role})").mkString(", ")
    _root_.com.histmersion.api.media.MediaMarkdown.MediaItemLineResult(s"- `${m.uid}` $origin · $lic${m.credit.fold("")(c => s" · $c")}$targets")

  def mediaList(l: MediaList): com.histmersion.api.media.MediaMarkdown.MediaListResult =
    OffsetPage.markdown(l.page, Plural.noun(l.total.value, _root_.com.histmersion.api.Plural.NounSingular("picture")), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No pictures._"), l.items)(mediaItemLine)

  def mediaItem(m: MediaItem): com.histmersion.api.media.MediaMarkdown.MediaItemResult =
    val origin = m.citekey
      .map(c => s"**Source:** [[$c]] / `${m.name}`")
      .getOrElse(
        s"**Provider:** ${m.provider.map(MediaProvider.title).getOrElse("?")} — `${m.providerKey.getOrElse(m.name)}`"
      )
    val rows = List(
      Some(origin),
      Some(s"**Licence:** ${m.license.fold("_none recorded_")(l => m.licenseUrl.fold(s"`$l`")(u => s"[`$l`]($u)"))}"),
      m.credit.map(c => s"**Credit:** $c"),
      m.author.map(a => s"**Author:** $a"),
      m.sourceUrl.map(u => s"**Page:** <$u>"),
      m.originalUrl.map(u => s"**Original:** <$u>"),
      m.retrievedAt.map(t => s"**Retrieved:** $t"),
      Some(
        s"**File:** ${m.mime} · ${Plural
            .count(m.bytes, "byte")}${(m.width, m.height) match { case (Some(w), Some(h)) => s" · $w×$h"; case _ => "" }} · `${m.fileUrl}`"
      ),
      m.thumbUrl.map(u => s"**Thumbnail:** `$u`"),
      m.kind.map(k => s"**Kind:** $k")
    ).flatten.mkString("  \n")
    val targets = if m.targets.isEmpty then ""
    else
      "\n\n**Of**\n" + m.targets
        .map(t =>
          s"- ${t.targetType} `${t.targetId}` — ${t.role}${(t.property, t.item) match {
              case (Some(p), Some(q)) => s" ($p of $q)"; case _ => ""
            }}" +
            t.basis.fold("")(b => s" — matched by $b")
        )
        .mkString("\n")
    val cap = m.caption.map(c => s"\n\n> $c").getOrElse("")
    _root_.com.histmersion.api.media.MediaMarkdown.MediaItemResult(s"# `${m.uid}`\n\n$rows$targets$cap")

  def mediaFacets(f: MediaFacets): com.histmersion.api.media.MediaMarkdown.MediaFacetsResult =
    s"**Providers:** ${countsLine(f.providers.map(c => c.modify(_.key).setTo(MediaProvider.title(c.key))))}  \n" +
      s"**Licences:** ${countsLine(f.licences)}"

  def figureLicenceFill(f: FigureLicenceFill): com.histmersion.api.media.MediaMarkdown.FigureLicenceFillResult =
    val what = if f.applied then s"${Plural.count(f.updated, _root_.com.histmersion.api.Plural.CountSingular("figure"))} given their source's licence"
    else s"${Plural.count(f.candidates, _root_.com.histmersion.api.Plural.CountSingular("figure"))} would take their source's licence (dry run; `--apply` writes it)"
    val by = if f.bySource.isEmpty then "" else "\n\n" + f.bySource.map(c => s"- [[${c.key}]]: ${c.n}").mkString("\n")
    _root_.com.histmersion.api.media.MediaMarkdown.FigureLicenceFillResult(s"# Figure licences\n\n$what$by")

  def assetLinkLine(l: AssetLink): com.histmersion.api.media.MediaMarkdown.AssetLinkLineResult =
    val xywh = l.xywh.map(x => s" xywh=$x").getOrElse("")
    _root_.com.histmersion.api.media.MediaMarkdown.AssetLinkLineResult(s"- **${l.relation}** ${l.targetType}:${l.targetId} ← ${l.citekey}/${l.assetName}$xywh")

  def assetLinkList(ll: AssetLink.List): com.histmersion.api.media.MediaMarkdown.AssetLinkListResult =
    OffsetPage.markdown(ll.page, _root_.com.histmersion.api.OffsetPage.MarkdownNoun("link(s)"), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No asset links._"), ll.links)(assetLinkLine)

  def assetLink(l: AssetLink): com.histmersion.api.media.MediaMarkdown.AssetLinkResult =
    val xywh = l.xywh.map(x => s"  \n**Region:** $x").getOrElse("")
    val notes = l.notes.map(n => s"  \n$n").getOrElse("")
    _root_.com.histmersion.api.media.MediaMarkdown.AssetLinkResult(s"# Link #${l.id}\n\n**Relation:** ${l.relation}  \n**Target:** ${l.targetType}:${l.targetId}  \n**Asset:** ${l.citekey}/${l.assetName}$xywh$notes")
