package com.histmersion
package api.media
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{OffsetPage, Plural}
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.catalogue.Year

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object MediaMarkdown:

  export RepairAcquire.RepairRequest.DryRun as MediaSamplesDryRun

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def providerTitle(p: MediaItem.Provider): MediaProvider.Info.Title =
    MediaProvider.title(MediaProvider.Id(p.value))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def assetDetail(ck: Citekey, a: AssetDetail): String =
    val loc = (a.lat, a.lon) match
      case (Some(lat), Some(lon)) => s"  \n**Location:** $lat, $lon"
      case _                      => ""
    val per = (a.periodStart, a.periodEnd) match
      case (Some(s), Some(e)) => s"  \n**Period:** ${Year.period(Some(s), Some(e))}"
      case _                  => ""
    val meta = List(
      a.kind.map(k => s"**Kind:** $k"),
      a.license.map(l => s"**License:** $l"),
      Some(s"**Size:** ${Plural.count(Plural.Count(a.bytes.value), Plural.Noun.Singular("byte"))}"),
      (a.width, a.height) match {
        case (Some(w), Some(h)) => Some(s"**Dimensions:** $w×$h")
        case _                  => None
      }
    ).flatten.mkString("  \n")
    val cap = a.caption.map(c => s"\n\n> $c").getOrElse("")
    val created = Creation.line(a.creator, a.createdAt).map(l => s"  \n_${l}_").getOrElse("")
    (s"# `$ck` / `${a.name}`\n\n$meta$loc$per$created$cap")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private[api] def countsLine(cs: List[MediaCount]): String =
    if cs.isEmpty then ("none")
    else
      (cs
        .map(c => s"${c.key} ${c.n}${if (c.allowed) == MediaCount.Allowed.Allowed then "" else " (not allowed)"}")
        .mkString(" · ")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mediaProviders(ps: List[MediaProvider.Info]): String =
    if ps.isEmpty then ("No media provider is registered (every media plugin is switched off).")
    else
      ps.map { p =>
        val origin = if (p.builtin) == MediaProvider.Info.Builtin.Builtin then s"built in, plugin `${p.plugin}`"
        else s"plugin `${p.plugin}`"
        val terms = if p.licences.value.trim.isEmpty then "" else s" · licences: ${p.licences}"
        s"- **`${p.name}`** — ${p.title} (${p.over}; $origin$terms)" +
          (if p.description.value.trim.isEmpty then "" else s"\n  ${p.description.value.trim}")
      }.mkString("## Media providers\n\n", "\n", "\n\n`histmersion media import <name>` runs one.")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  def mediaImportReport(r: MediaImportReport): String =
    val mode = if (r.dryRun) == RepairAcquire.RepairRequest.DryRun.DryRun then "dry run — nothing stored" else "stored"
    val licences = if r.byLicence.isEmpty then "_none_"
    else
      r.byLicence
        .map(c => s"| `${c.key}` | ${c.n} | ${if (c.allowed) == MediaCount.Allowed.Allowed then "yes" else "**no**"} |")
        .mkString("| licence | files | allowed |\n|---|---|---|\n", "\n", "")
    val rejected = if r.rejected.isEmpty then ""
    else "\n\n**Not stored**\n" + r.rejected.map(c => s"- ${c.key}: ${c.n}").mkString("\n")
    val done = if (r.dryRun) == RepairAcquire.RepairRequest.DryRun.DryRun then ""
    else
      s"\n\n**Stored:** ${r.stored} new · ${r.updated} refreshed · ${r.linked} link(s) · ${r.categories} Commons ${Plural.noun(Plural.Count(r.categories.value.toLong), Plural.Noun.Singular("category"), Plural.Noun.Plural("categories"))}"
    val revoked = if r.revoked.isEmpty then ""
    else "\n\n**Licence no longer allowed (kept — review):**\n" + r.revoked.map(x => s"- $x").mkString("\n")
    val failed = if r.failed.isEmpty then "" else "\n\n**Failed**\n" + r.failed.map(x => s"- $x").mkString("\n")
    val pending =
      s"\n\n**Not asked yet:** ${Plural.count(Plural.Count(r.pending.value.toLong), Plural.CountSingular("place/event"), Plural.CountPlural("places/events"))}" +
        (if r.batches.value > 0 then
           s" · ${Plural.count(Plural.Count(r.batches.value.toLong), Plural.CountSingular("batch"), Plural.CountPlural("batches"))} run"
         else "")
    // assembled line by line, not with stripMargin: the table's rows start with `|` themselves
    List(
      s"# Media import · ${providerTitle(r.provider)} ($mode)",
      "",
      (if MediaProvider.all.contains(r.provider) && r.provider != MediaItem.Provider(MediaProvider.Commons) then
         s"**Asked about:** ${Plural.count(Plural.Count(r.targets.value.toLong), Plural.Noun.Singular("place"))} · ${Plural.count(Plural.Count(r.items.value.toLong), Plural.Noun.Singular("record"))} read · " +
           s"${Plural.count(Plural.Count(r.statements.value.toLong), Plural.CountSingular("match"), Plural.CountPlural("matches"))} · ${Plural.count(Plural.Count(r.files.value.toLong), Plural.CountSingular("picture"))}  "
       else if r.provider == MediaItem.Provider(MediaProvider.Pleiades) then
         s"**Asked about:** ${Plural.count(Plural.Count(r.targets.value.toLong), Plural.Noun.Singular("place"))} · ${Plural.count(Plural.Count(r.items.value.toLong), Plural.Noun.Singular("Pleiades record"))} read · " +
           s"${Plural.count(Plural.Count(r.statements.value.toLong), Plural.CountSingular("linked Commons file"))} · ${Plural.count(Plural.Count(r.files.value.toLong), Plural.CountSingular("file"))}  "
       else
         s"**Asked about:** ${Plural.count(Plural.Count(r.targets.value.toLong), Plural.Noun.Singular("place/event"), Plural.Noun.Plural("places/events"))} · ${Plural.count(Plural.Count(r.items.value.toLong), Plural.Noun.Singular("Wikidata item"))} · " +
           s"${Plural.count(Plural.Count(r.statements.value.toLong), Plural.CountSingular("picture statement"))} · ${Plural.count(Plural.Count(r.files.value.toLong), Plural.CountSingular("file"))}  "
      ) ,
      s"**By provider:** ${providerTitle(r.provider)} ${r.files}  ",
      s"**By role:** ${countsLine(r.byRole)}",
      "",
      licences + rejected + mediaSamples(r.samples, r.dryRun) + done + revoked + failed + pending
    ).mkString("\n")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  private[api] def mediaSamples(xs: List[MediaSample], dryRun: MediaMarkdown.MediaSamplesDryRun): String =
    if xs.isEmpty then ("")
    else
      (s"\n\n**${if (dryRun).dryRun then "Would link" else "Linked"} (sample of ${xs.size})**\n" +
        xs.map(x =>
          s"- ${x.targetType} `${x.targetId}` (${x.targetLabel}) ← ${x.title} [`${x.key}`](${x.url}) · `${x.license}` — ${x.basis}"
        ).mkString("\n")
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mediaCoversReport(r: MediaCoversReport): String =
    val mode = if (r.dryRun) == RepairAcquire.RepairRequest.DryRun.DryRun then "dry run — nothing stored" else "stored"
    val verb = if (r.dryRun) == RepairAcquire.RepairRequest.DryRun.DryRun then "would link" else "linked"
    val lines = if r.lines.isEmpty then ""
    else
      "\n\n" + r.lines
        .map(l => s"- [[${l.citekey}]] — ${l.role} from ${providerTitle(l.provider)}: `${l.detail}`")
        .mkString("\n")
    val skipped =
      if r.skipped.isEmpty then "" else "\n\n**None for**\n" + r.skipped.map(c => s"- ${c.key}: ${c.n}").mkString("\n")
    val failed = if r.failed.isEmpty then "" else "\n\n**Failed**\n" + r.failed.map(x => s"- $x").mkString("\n")
    s"# Source pictures ($mode)\n\n${Plural.count(Plural.Count(r.sources.value.toLong), Plural.CountSingular("source"))} looked at · $verb " +
      s"${Plural.count(Plural.Count(r.covers.value.toLong), Plural.CountSingular("cover"))} and ${Plural.count(Plural.Count(r.illustrations.value.toLong), Plural.CountSingular("illustration"))}" +
      lines + skipped + failed

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mediaItemLine(m: MediaItem): String =
    val origin = m.citekey
      .map(c => s"[[$c]] / `${m.name}`")
      .getOrElse(s"${m.provider.map(providerTitle).getOrElse("?")} `${m.providerKey.getOrElse(m.name)}`")
    val lic = m.license.fold(if m.provider.exists(p => MediaProvider.covers(MediaProvider.Id(p.value))) then
      "a cover (no reuse licence; shown locally)"
    else "**no licence**")(l => s"`$l`")
    val targets = if m.targets.isEmpty then ""
    else " → " + m.targets.map(t => s"${t.targetType}:${t.targetId} (${t.role})").mkString(", ")
    (s"- `${m.uid}` $origin · $lic${m.credit.fold("")(c => s" · $c")}$targets")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def mediaList(l: MediaList): String =
    OffsetPage
      .markdown(
        l.page,
        Plural.noun(Plural.Count(l.total.value.toLong), Plural.Noun.Singular("picture")).value,
        ("_No pictures._"),
        l.items
      )(mediaItemLine)
      .value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mediaItem(m: MediaItem): String =
    val origin = m.citekey
      .map(c => s"**Source:** [[$c]] / `${m.name}`")
      .getOrElse(
        s"**Provider:** ${m.provider.map(providerTitle).getOrElse("?")} — `${m.providerKey.getOrElse(m.name)}`"
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
            .count(Plural.Count(m.bytes.value), Plural.Noun.Singular("byte"))}${(m.width, m.height) match {
            case (Some(w), Some(h)) => s" · $w×$h"; case _ => ""
          }} · `${m.fileUrl}`"
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
    (s"# `${m.uid}`\n\n$rows$targets$cap")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mediaFacets(f: MediaFacets): String =
    s"**Providers:** ${countsLine(f.providers.map(c => c.modify(_.key).setTo(MediaCount.Key(providerTitle(MediaItem.Provider(c.key.value)).value))))}  \n" +
      s"**Licences:** ${countsLine(f.licences)}"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def figureLicenceFill(f: FigureLicenceFill): String =
    val what = if (f.applied) == FigureLicenceFill.Applied.Applied then
      s"${Plural.count(Plural.Count(f.updated.value.toLong), Plural.CountSingular("figure"))} given their source's licence"
    else
      s"${Plural.count(Plural.Count(f.candidates.value.toLong), Plural.CountSingular("figure"))} would take their source's licence (dry run; `--apply` writes it)"
    val by = if f.bySource.isEmpty then "" else "\n\n" + f.bySource.map(c => s"- [[${c.key}]]: ${c.n}").mkString("\n")
    (s"# Figure licences\n\n$what$by")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def assetLinkLine(l: AssetLink): String =
    val xywh = l.xywh.map(x => s" xywh=$x").getOrElse("")
    (s"- **${l.relation}** ${l.targetType}:${l.targetId} ← ${l.citekey}/${l.assetName}$xywh")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def assetLinkList(ll: AssetLink.List): String =
    OffsetPage.markdown(ll.page, ("link(s)"), ("_No asset links._"), ll.links)(assetLinkLine).value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def assetLink(l: AssetLink): String =
    val xywh = l.xywh.map(x => s"  \n**Region:** $x").getOrElse("")
    val notes = l.notes.map(n => s"  \n$n").getOrElse("")
    (s"# Link #${l.id}\n\n**Relation:** ${l.relation}  \n**Target:** ${l.targetType}:${l.targetId}  \n**Asset:** ${l.citekey}/${l.assetName}$xywh$notes")
