package com.histmersion
package api.library
import com.histmersion.api.Plural
import com.histmersion.api.Markdown.*
import com.histmersion.api.media.AssetLinks
import com.histmersion.api.automation.Creation

object SourcesMarkdown:

  type SourceRenamedListXs = String
  object SourceRenamedListXs:
    inline def apply(v: String): String = v

  def sourceFindingsOnly(findings: Vector[Finding.Row]): String =
    if findings.isEmpty then ("_(no findings)_")
    else
      val items = findings.map { f =>
        val loc = place(f.regionName, f.lat, f.lon)
        val p = period(f.periodStart, f.periodEnd)
        val sig = f.significance.map(s => s"[$s]").getOrElse("")
        val tail = List(
          if loc.nonEmpty then s"@ $loc" else "",
          p,
          f.confidence.map(c => s"(${c.toString.toLowerCase})").getOrElse(""),
          sig
        )
          .filter(_.nonEmpty)
          .mkString(" · ")
        s"- ${f.claim}${if tail.nonEmpty then s"  \n  _${tail}_" else ""}"
      }
      (s"## Findings (${findings.size})\n${items.mkString("\n")}")

  def source(s: Source.Detail, findings: Vector[Finding.Row], fullText: Boolean): String =
    val authorLinks = if s.authors.map(_.value).nonEmpty then chipLinks(s.authors.map(_.value), "author") else ""
    val meta = List(
      authorLinks,
      year(s.year.map(_.value.toInt)),
      s.venue.map(_.value).getOrElse(""),
      s.status.wire.value
    ).filter(_.nonEmpty).mkString(" · ")
    val per = period(s.periodStart, s.periodEnd)
    val header = s"# ${s.title}\n\n_$meta${if per.nonEmpty then s" · $per" else ""}_"
    val topics = if s.topics.nonEmpty then s"\n\n**Topics:** ${chipLinks(s.topics.map(_.value), "topic")}" else ""
    val regions = if s.regions.nonEmpty then s"  \n**Regions:** ${chipLinks(s.regions.map(_.value), "place")}" else ""
    val tags = if s.tags.nonEmpty then s"  \n**Tags:** ${chipLinks(s.tags.map(_.value), "tag")}" else ""
    val doi = s.doi.map(_.value).map(d => s"  \n**DOI:** $d").getOrElse("")
    val prov = s.provenance.map(_.value).filter(_.nonEmpty).map(p => s"  \n**Provenance:** $p").getOrElse("")
    val uid = s.uid.map(u => s"  \n**Uid:** $u").getOrElse("")
    val created = Creation.line(s.creator, s.createdAt).map(l => s"  \n_${l}_").getOrElse("")
    val summary = s.summary.map(x => s"\n\n${x.value.trim}").getOrElse("")
    val notes = s.notes.map(_.value.trim).filter(_.nonEmpty).map(n => s"\n\n## Notes\n\n$n").getOrElse("")
    val findingsMd =
      if findings.isEmpty then ""
      else
        val items = findings.map { f =>
          val loc = place(f.regionName, f.lat, f.lon)
          val p = period(f.periodStart, f.periodEnd)
          val sig = f.significance.map(s => s"[$s]").getOrElse("")
          val tail = List(
            if loc.nonEmpty then s"@ $loc" else "",
            p,
            f.confidence.map(c => s"(${c.toString.toLowerCase})").getOrElse(""),
            sig,
            Creation.line(f.creator, f.createdAt).getOrElse("")
          )
            .filter(_.nonEmpty)
            .mkString(" · ")
          s"- ${f.claim}${if tail.nonEmpty then s"  \n  _${tail}_" else ""}"
        }
        s"\n\n## Findings (${findings.size})\n${items.mkString("\n")}"
    // Normalised here as well as at import: older bodies still carry `/Users/…/figures/x.png` links, which a
    // browser resolves against the server root and draws 0×0.
    val body =
      if !(fullText) then ""
      else
        s.ocrMarkdown
          .map(t =>
            s"\n\n---\n\n${AssetLinks.normalizeBody(AssetLinks.Body(t.value), AssetLinks.Title(s.title.value))}"
          )
          .getOrElse("\n\n_(no full text stored)_")
    (header + topics + regions + tags + doi + prov + uid + created + summary + notes + findingsMd + body)

  def neighbors(citekey: Citekey, rows: Vector[NeighborRow]): String =
    if rows.isEmpty then
      (s"_No references recorded for **$citekey**._ An empty result is not evidence of agreement — " +
        "disagreement edges are sparse (the corpus has only a handful); use `contradictions` for a focused view.")
    else
      def block(title: String, rs: Vector[NeighborRow])(
          item: NeighborRow => Source.Neighbor.Line
      ): String =
        if rs.isEmpty then ("")
        else (s"\n\n**$title**\n${rs.map(r => s"- _${Edge.Kind.wireOf(r.kind)}_ → ${item(r)}").mkString("\n")}")
      def sources(dir: ContradictionRow.Direction.Wire) =
        rows.filter(r => r.target == NeighborTarget.Source && r.direction == dir)
      val sourceItem: NeighborRow => Source.Neighbor.Line = r =>
        val st = r.status.map(s => s" _(${s.wire})_").getOrElse("")
        val created = Creation.line(r.creator, r.createdAt).map(l => s" · _${l}_").getOrElse("")
        Source.Neighbor
          .Line(s"[[${r.citekey}]]${r.title.map(t => s" — $t").getOrElse("")}$st$created")
      s"### Sources · $citekey" +
        block(
          ("Outgoing (this → other)"),
          sources(ContradictionRow.Direction.Wire("out"))
        )(sourceItem) +
        block(
          "Incoming (other → this)",
          sources(ContradictionRow.Direction.Wire("in"))
        )(sourceItem) +
        block("Articles", rows.filter(_.target == NeighborTarget.Article))(r =>
          Source.Neighbor.Line(
            s"[${r.title.getOrElse(Label.display(Label.Text(r.citekey.value)))}](/article/${r.citekey})"
          )
        ) +
        block("Not in the corpus yet", rows.filter(_.target == NeighborTarget.Unresolved))(r =>
          Source.Neighbor.Line(s"**${r.citekey}**")
        )

  def graph(g: Source.Graph): String =
    if g.nodes.isEmpty then (s"_No graph data for **${g.root}**._")
    else
      val header =
        s"### Source graph · ${g.root} (depth ${g.depth}, ${Plural.count(Plural.CountN((g.nodes.size).toLong), Plural.CountSingular("node"))}, ${Plural.count(Plural.CountN((g.links.size).toLong), Plural.CountSingular("link"))})"
      val nodeLines = g.nodes
        .sortBy(n => ((n.depth.value: Int), (n.id.value: String)))
        .map { n =>
          val year = n.year.map(y => s" ($y)").getOrElse("")
          val st = n.status.map(s => s" _[${s.wire}]_").getOrElse("")
          val depth = if n.depth.value > 0 then s" d=${n.depth}" else " **root**"
          s"- [[${n.id}]]$year$st$depth${n.title.map(t => s" — $t").getOrElse("")}"
        }
        .mkString("\n")
      val linkLines = g.links
        .map { l =>
          s"- `${l.source}` →_${Edge.Kind.wireOf(l.kind)}_ `${l.target}`"
        }
        .mkString("\n")
      (s"$header\n\n**Nodes**\n$nodeLines\n\n**Links**\n$linkLines")

  def contradictions(result: ContradictionsResult, citekey: Option[Citekey]): String =
    if result.rows.isEmpty then
      citekey match
        case Some(ck) =>
          (s"_No disagreement edges or contested status for **$ck**._ An empty result is not evidence " +
            "of agreement — disagreement edges are extremely sparse in the corpus.")
        case None =>
          ("_No contradictions found in the corpus._")
    else
      val items = result.rows.map { r =>
        val edge = (r.direction, r.kind, r.otherCitekey) match
          case (Some(dir), Some(k), Some(other)) =>
            val arrow =
              if dir == ContradictionRow.Direction.Wire("out") then "→" else "←"
            val otherInfo = List(
              r.otherTitle.map(_.value).getOrElse(""),
              r.otherYear.map(_.toString).getOrElse(""),
              r.otherStatus.fold("")(_.wire.value)
            ).filter(_.nonEmpty).mkString(" · ")
            s"${Edge.Kind.wireOf(k)} $arrow **$other** ($otherInfo)"
          case _ => s"status: ${r.status.wire}"
        s"- **${r.citekey}** (${year(r.year.map(_.value.toInt))} · ${r.status.wire}) — $edge"
      }
      val scope = citekey.map(ck => s" · $ck").getOrElse("")
      s"### ${result.total} contradiction(s)$scope\n\n${items.mkString("\n")}\n\n" +
        "_Edge kinds: references, challenges, refutes, updates, superseded_by. " +
        "An empty result is not evidence of agreement._"

  def sourceRenamed(r: Source.Renamed): String =
    val c = r.counts
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"`$x`").mkString(", ")
    val columns =
      if c.columns.isEmpty then ""
      else "\n" + c.columns.map(n => s"  - ${n.table}: ${n.rows}").mkString("\n")
    val calibre = r.calibreId match
      case Some(id) =>
        s"Calibre: book $id is linked by its id, and Calibre never stores the citekey — nothing to change there."
      case None =>
        s"Calibre: not pushed yet. `histmersion calibre push ${r.newCitekey}` looks for " +
          s"`<cli.calibre.originals-dir>/${r.newCitekey}/paper.*` — rename the `${r.oldCitekey}` " +
          "directory there first, or pass `--file`."
    s"""# Renamed `${r.oldCitekey}` → `${r.newCitekey}`
       |
       |**${r.source.title}**${r.source.year.fold("")(y => s" ($y)")}
       |
       |- findings renumbered: ${c.findings}
       |- articles rewritten (a version recorded for each): ${list(c.articles.map(_.value))}
       |- source notes/summaries rewritten: ${list(c.sources.map(_.value))}
       |- `[[links]]` rewritten: ${c.links}
       |- parked edges promoted: ${c.edgesPromoted}
       |- other columns:${
        if c.columns.isEmpty then " none" else columns
      }
       |
       |`${r.oldCitekey}` is now an alias: every read, URL and `[[${r.oldCitekey}]]` still resolves to `${r.newCitekey}`.
       |
       |$calibre
       |
       |Mirror: the next full `histmersion export` removes `sources/${r.oldCitekey}.md`.""".stripMargin
