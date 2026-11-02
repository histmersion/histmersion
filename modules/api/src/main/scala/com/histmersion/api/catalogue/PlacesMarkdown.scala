package com.histmersion.api.catalogue
import com.histmersion.api.Markdown.*

object PlacesMarkdown:

  private[api] def kindOf(kind: String, ps: Option[Long], pe: Option[Long]): String =
    val p = period(ps, pe)
    if p.isEmpty then kind else s"$kind · $p"

  private[api] def kindWhen(s: PlaceSummary): String =
    if s.dating == PlaceDating.Stated then kindOf(s.kind, s.periodStart, s.periodEnd) else s"${s.kind} · ${s.when}"

  def places(rows: Vector[PlaceHit], asOf: Option[Long] = None): String =
    if rows.isEmpty then "_No matching places._"
    else
      val items = rows.map { h =>
        val alt = h.matchedName.filter(!_.equalsIgnoreCase(h.label)).map(n => s" (matched “$n”)").getOrElse("")
        val src = h.source.map(s => s" · $s").getOrElse("")
        val meta = s"${kindWhen(h.toSummary)} · ${h.findings} finding(s)$src"
        s"- **${h.label}**${contestedMark(h.contested, h.label)}$alt — `${h.id}`  \n  _${meta}_"
      }
      s"### ${rows.size} place(s)${asOfNote(asOf)}\n\n${items.mkString("\n")}"

  def placeTree(rows: Vector[PlaceTreeRow]): String =
    if rows.isEmpty then "_No hierarchy._"
    else
      rows
        .map { r =>
          s"${"  " * r.depth}- **${r.label}**${contestedMark(r.contested, r.label)} `${r.id}`" +
            s" _(${r.kind}${if r.findings > 0 then s", ${r.findings} finding(s)" else ""})_"
        }
        .mkString("\n")

  def placeImport(r: PlaceImportResult): String =
    val head = r.job.fold(s"imported ${r.places} place(s), ${r.names} name(s), ${r.relations} relation(s)")(id =>
      s"job `$id` started (`histmersion jobs $id` follows it)"
    )
    if r.failed.isEmpty then head
    else s"$head\n\nskipped:\n${r.failed.map(f => s"- $f").mkString("\n")}"

  def gazetteerSources(rows: List[GazetteerSourceInfo]): String =
    if rows.isEmpty then "_No external gazetteer sources: no pinned extracts and no LPF files._"
    else
      val head =
        "| source | file | pinned | imported | places | names | links | licence |\n|---|---|---|---|---:|---:|---:|---|"
      val body = rows.map { r =>
        val file = r.file.fold("—")(f => s"`$f`" + (if r.present then "" else " (missing)"))
        val imported = r.importedVersion.fold("—")(v => s"$v (${r.importedAt.getOrElse("").take(10)})")
        val n = (x: Option[Int]) => x.fold("—")(_.toString)
        s"| ${r.key} — ${r.title} | $file | ${r.pinnedVersion.getOrElse("—")} | $imported | ${n(r.places)} | " +
          s"${n(r.names)} | ${n(r.links)} | ${r.licence.getOrElse("—")} |"
      }
      (head :: body).mkString("\n")

  def placeNameChanged(r: PlaceNameChanged): String =
    val lang = if r.lang.isEmpty then "" else s" (${r.lang})"
    val what =
      if !r.added then s"removed the name **${r.name}**$lang from"
      else if !r.changed then s"**${r.name}**$lang was already a name added by hand to"
      else if r.wasImported then s"kept **${r.name}**$lang (a name the seed gives) as one added by hand, on"
      else s"added the name **${r.name}**$lang to"
    s"$what **${r.label}** `${r.placeId}` — names added by hand survive every re-seed"

  def manualPlaceNames(rows: List[ManualPlaceName]): String =
    if rows.isEmpty then "_No names added by hand._"
    else
      s"${rows.size} name(s) added by hand:\n\n" + rows
        .map { r =>
          s"- **${r.name}**${if r.lang.isEmpty then "" else s" (${r.lang})"} → ${r.label} `${r.placeId}`"
        }
        .mkString("\n")

  def placeOverrideChanged(r: PlaceOverrideChanged): String =
    val kind = if r.kindBefore == r.kindAfter then s"kind ${r.kindAfter}" else s"kind ${r.kindBefore} → ${r.kindAfter}"
    val period = if r.periodBefore == r.periodAfter then s"period ${r.periodAfter}"
    else s"period ${r.periodBefore} → ${r.periodAfter}"
    val point = (r.latBefore, r.lonBefore, r.latAfter, r.lonAfter) match
      case (Some(a), Some(b), Some(c), Some(d)) if a != c || b != d =>
        s", point ${PlaceOverrides.pointText(a, b)} → ${PlaceOverrides.pointText(c, d)} (" +
          String.format(java.util.Locale.ROOT, "%.0f km", Geocode.distanceKm(b, a, d, c)) + ")"
      case (_, _, Some(c), Some(d)) => s", point ${PlaceOverrides.pointText(c, d)}"
      case _                        => ""
    val head =
      if r.reset then s"reset **${r.label}** `${r.placeId}` to what its source says: $kind, $period$point"
      else s"set **${r.label}** `${r.placeId}`: $kind, $period$point — kept by every re-seed and re-import"
    val lost = if r.unlocated == 0 then "" else s"; **${r.unlocated}** of them now have no place at all"
    s"$head\n\nfindings matched again: ${r.findingsBefore} → ${r.findingsAfter}$lost"

  def placeGone(r: PlaceGone): String =
    val head = r.into match
      case Some(into) =>
        s"merged **${r.label}** `${r.placeId}` into **${r.intoLabel.getOrElse(into)}** `$into` — `${r.placeId}` now resolves to it"
      case None => s"removed **${r.label}** `${r.placeId}`"
    val lost = if r.unlocated == 0 then "" else s"; **${r.unlocated}** of them now have no place at all"
    s"$head; no re-seed brings it back\n\nfindings matched again: ${r.findingsBefore} → ${r.findingsAfter}$lost"

  def placeOverrides(rows: List[PlaceOverride]): String =
    if rows.isEmpty then "_No place has a kind, period or point set by hand._"
    else
      s"${rows.size} place(s) with a kind, period or point set by hand:\n\n" + rows
        .map { r =>
          val pt = for la <- r.lat; lo <- r.lon yield s"point ${PlaceOverrides.pointText(la, lo)}"
          val set =
            r.kind.toList ++ Option.when(r.periodSet)(PlaceOverrides.periodText(r.periodStart, r.periodEnd)).toList ++
              pt.toList
          val srcPt = if pt.isEmpty then ""
          else
            (for la <- r.sourceLat; lo <- r.sourceLon
            yield s", point ${PlaceOverrides.pointText(la, lo)}").getOrElse(", no point")
          val src = s"${r.sourceKind}, ${PlaceOverrides.periodText(r.sourcePeriodStart, r.sourcePeriodEnd)}$srcPt"
          s"- **${r.label}** `${r.placeId}`: ${set.mkString(", ")} _(the source says: $src)_" +
            r.reason.fold("")(w => s" — $w")
        }
        .mkString("\n")

  def placeAdded(p: PlaceSummary): String =
    s"added **${p.label}** `${p.id}` _(${kindOf(p.kind, p.periodStart, p.periodEnd)})_" +
      p.source.fold("")(src => s" — source: $src") +
      "\n\nIt is this collection's own place (carried in a bundle, never touched by a re-seed). " +
      "Run `histmersion places rebuild` to match findings to it."

  def placeRebuild(r: PlaceRebuildResult): String =
    s"${r.links} finding→place link(s) over ${r.places} place(s) " +
      s"(${r.byGeometry} by geometry, ${r.byName} by region name); " +
      s"${r.located} finding(s) located, ${r.unlocated} unlocated"

  def place(d: PlaceCard, tree: Vector[PlaceTreeRow] = Vector.empty): String =
    val p = d.place
    val header = s"# ${p.label}${contestedMark(d.contested || p.contested, p.label)}" +
      s"\n\n_`${p.id}` · ${kindWhen(p)}" +
      s" · ${d.findings} finding(s)${asOfNote(d.asOf)}_"
    val what = d.descriptions.headOption.map(x => s"\n\n${x.text}").getOrElse("")
    val src = p.source.map(s => s"  \n**Source:** $s").getOrElse("") +
      d.siteType.flatMap(SiteTypes.get).map(t => s"  \n**Site type:** ${t.label}").getOrElse("")
    val shape = d.shapeNote.map(n => s"  \n**Shape:** $n").getOrElse("") +
      d.locationNote.map(n => s"  \n**Location:** $n").getOrElse("")
    val dating = if d.datedByFindings then s"  \n**Dating:** ${PlaceTimeBasis.FindingsNote}" else ""
    val names = if p.names.isEmpty then "" else s"  \n**Also known as:** ${p.names}"
    val geom = d.geometry
      .map(g => s"  \n**Geometry:** ${g.take(40).takeWhile(_ != ',')}… (GeoJSON)")
      .getOrElse("  \n**Geometry:** _none_")
    def list(title: String, xs: List[PlaceSummary]) =
      if xs.isEmpty then ""
      else
        s"\n\n**$title**\n" +
          xs.map(x => s"- ${x.label}${contestedMark(x.contested, x.label)} `${x.id}` _(${x.kind})_")
            .mkString("\n")
    val fps = if d.footprints.isEmpty then ""
    else
      "\n\n**Footprints (" + d.footprints.size + ")**\n" +
        d.footprints
          .map { f =>
            val per = f.period.map(p => s" · ${periodLabel(Some(p.start), Some(p.end))}").getOrElse("")
            val cert = f.certainty.map(c => s" · _${c}_").getOrElse("")
            val ck = f.citekey.map(c => s" [[$c]]").getOrElse("")
            s"- ${f.role}$per$cert$ck"
          }
          .mkString("\n")
    val lks = if d.links.isEmpty then ""
    else
      "\n\n**External links**\n" +
        d.links
          .map { l =>
            val id = PlaceLink.url(l.scheme, l.externalId).fold(s"`${l.externalId}`")(u => s"[${l.externalId}]($u)")
            s"- ${l.scheme}: $id (${l.relation})"
          }
          .mkString("\n")
    val tps = if d.types.isEmpty then ""
    else
      "\n\n**Types**\n" +
        d.types
          .map { t =>
            val per = t.period.map(p => s" · ${periodLabel(Some(p.start), Some(p.end))}").getOrElse("")
            s"- ${t.typeLabel}$per"
          }
          .mkString("\n")
    val hier = if tree.isEmpty then "" else s"\n\n## Hierarchy\n\n${placeTree(tree)}"
    val rec = d.record
    val record =
      rec.dateSource.map(s => s"  \n**Dates from:** ${PlaceRecord.dateSourceText(s)}").getOrElse("") +
        (if rec.periods.isEmpty then "" else s"  \n**Time periods:** ${rec.periods.mkString(", ")}") +
        (if rec.designations.isEmpty then ""
         else
           "\n\n**Heritage designations**\n" + rec.designations
             .map { h =>
               val when = List(h.start.map(x => s"from $x"), h.end.map(x => s"until $x")).flatten.mkString(" ")
               s"- ${h.label.getOrElse(h.id)} `${h.id}`${if when.isEmpty then "" else s" ($when)"}"
             }
             .mkString("\n")) +
        (if rec.registers.isEmpty then ""
         else "\n\n**Listed in**\n" + rec.registers.map(r => s"- ${r.name}: `${r.id}`").mkString("\n"))
    header + what + src + shape + dating + names + geom + record + fps + lks + tps +
      list("Part of", d.parents) + list("Contains", d.children) + hier
