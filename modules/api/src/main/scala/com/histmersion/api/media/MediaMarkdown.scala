package com.histmersion.api.media
import com.histmersion.api.{OffsetPage, Plural}
import com.histmersion.api.Markdown.*

object MediaMarkdown:

  def assetDetail(ck: String, a: AssetDetail): String =
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
    s"# `$ck` / `${a.name}`\n\n$meta$loc$per$cap"

  private[api] def countsLine(cs: List[MediaCount]): String =
    if cs.isEmpty then "none"
    else cs.map(c => s"${c.key} ${c.n}${if c.allowed then "" else " (not allowed)"}").mkString(" · ")

  def mediaProviders(ps: List[MediaProvider.Info]): String =
    if ps.isEmpty then "No media provider is registered (every media plugin is switched off)."
    else
      ps.map { p =>
        val origin = if p.builtin then s"built in, plugin `${p.plugin}`" else s"plugin `${p.plugin}`"
        val terms = if p.licences.trim.isEmpty then "" else s" · licences: ${p.licences}"
        s"- **`${p.name}`** — ${p.title} (${p.over}; $origin$terms)" +
          (if p.description.trim.isEmpty then "" else s"\n  ${p.description.trim}")
      }.mkString("## Media providers\n\n", "\n", "\n\n`histmersion media import <name>` runs one.")

  def mediaImportReport(r: MediaImportReport): String =
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
      s"\n\n**Stored:** ${r.stored} new · ${r.updated} refreshed · ${r.linked} link(s) · ${r.categories} Commons ${Plural.noun(r.categories, "category", "categories")}"
    val revoked = if r.revoked.isEmpty then ""
    else "\n\n**Licence no longer allowed (kept — review):**\n" + r.revoked.map(x => s"- $x").mkString("\n")
    val failed = if r.failed.isEmpty then "" else "\n\n**Failed**\n" + r.failed.map(x => s"- $x").mkString("\n")
    val pending = s"\n\n**Not asked yet:** ${Plural.count(r.pending, "place/event", "places/events")}" +
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

  private[api] def mediaSamples(xs: List[MediaSample], dryRun: Boolean): String =
    if xs.isEmpty then ""
    else
      s"\n\n**${if dryRun then "Would link" else "Linked"} (sample of ${xs.size})**\n" +
        xs.map(x =>
          s"- ${x.targetType} `${x.targetId}` (${x.targetLabel}) ← ${x.title} [`${x.key}`](${x.url}) · `${x.license}` — ${x.basis}"
        ).mkString("\n")

  def mediaCoversReport(r: MediaCoversReport): String =
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
    s"# Source pictures ($mode)\n\n${Plural.count(r.sources, "source")} looked at · $verb " +
      s"${Plural.count(r.covers, "cover")} and ${Plural.count(r.illustrations, "illustration")}" +
      lines + skipped + failed

  def mediaItemLine(m: MediaItem): String =
    val origin = m.citekey
      .map(c => s"[[$c]] / `${m.name}`")
      .getOrElse(s"${m.provider.map(MediaProvider.title).getOrElse("?")} `${m.providerKey.getOrElse(m.name)}`")
    val lic = m.license.fold(if m.provider.exists(MediaProvider.covers) then "a cover (no reuse licence; shown locally)"
    else "**no licence**")(l => s"`$l`")
    val targets = if m.targets.isEmpty then ""
    else " → " + m.targets.map(t => s"${t.targetType}:${t.targetId} (${t.role})").mkString(", ")
    s"- `${m.uid}` $origin · $lic${m.credit.fold("")(c => s" · $c")}$targets"

  def mediaList(l: MediaList): String =
    OffsetPage.markdown(l.page, Plural.noun(l.total, "picture"), "_No pictures._", l.items)(mediaItemLine)

  def mediaItem(m: MediaItem): String =
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
    s"# `${m.uid}`\n\n$rows$targets$cap"

  def mediaFacets(f: MediaFacets): String =
    s"**Providers:** ${countsLine(f.providers.map(c => c.copy(key = MediaProvider.title(c.key))))}  \n" +
      s"**Licences:** ${countsLine(f.licences)}"

  def figureLicenceFill(f: FigureLicenceFill): String =
    val what = if f.applied then s"${Plural.count(f.updated, "figure")} given their source's licence"
    else s"${Plural.count(f.candidates, "figure")} would take their source's licence (dry run; `--apply` writes it)"
    val by = if f.bySource.isEmpty then "" else "\n\n" + f.bySource.map(c => s"- [[${c.key}]]: ${c.n}").mkString("\n")
    s"# Figure licences\n\n$what$by"

  def assetLinkLine(l: AssetLink): String =
    val xywh = l.xywh.map(x => s" xywh=$x").getOrElse("")
    s"- **${l.relation}** ${l.targetType}:${l.targetId} ← ${l.citekey}/${l.assetName}$xywh"

  def assetLinkList(ll: AssetLink.List): String =
    OffsetPage.markdown(ll.page, "link(s)", "_No asset links._", ll.links)(assetLinkLine)

  def assetLink(l: AssetLink): String =
    val xywh = l.xywh.map(x => s"  \n**Region:** $x").getOrElse("")
    val notes = l.notes.map(n => s"  \n$n").getOrElse("")
    s"# Link #${l.id}\n\n**Relation:** ${l.relation}  \n**Target:** ${l.targetType}:${l.targetId}  \n**Asset:** ${l.citekey}/${l.assetName}$xywh$notes"
