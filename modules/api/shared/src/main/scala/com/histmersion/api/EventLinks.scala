package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Linking events to the corpus (#1449), deterministically — no model: the sources whose FINDINGS lie
  * near an event in time and space, and the sources whose TEXT names it (any of its names — its label,
  * the catalogue's other labels and aliases in other languages). Such a source is a PROPOSED link, kept
  * with its evidence (the finding ids, the passages with their character offsets) and a score; it is
  * shown as "possibly discussed in", unconfirmed. Confirming one writes an ATTESTATION — and only from
  * a passage that names the event: a source no passage of which names it is never attested by this
  * path, however close its findings are.
  *
  * And the QUESTIONS an event raises — a catalogue event no source discusses, with the corpus's findings
  * around it counted — as factual leads with the commands that act on them. */

/** One passage of a source that names an event: `[start, end)` are character offsets into the source's
  * body (the offsets `get_source` and `passages` use), `quote` the matched text, `name` the name it
  * matched, `context` the text around it. */
final case class EventPassage(start: Int, end: Int, quote: String, name: String, context: String = "")
    derives KindlingsCodecAsObject
object EventPassage:
  given Schema[EventPassage] = KindlingsSchema.derived[EventPassage].schema

object EventLinkStatus:
  val Proposed  = "proposed"
  val Confirmed = "confirmed"
  val Rejected  = "rejected"
  val all: List[String] = List(Proposed, Confirmed, Rejected)

/** One source that may discuss an event: its score, the findings near the event, the passages naming
  * it, and the reasons in words. `status`: proposed | confirmed (an attestation exists) | rejected. */
final case class EventLinkCandidate(
    eventId: String,
    citekey: String,
    score: Double,
    status: String = EventLinkStatus.Proposed,
    title: Option[String] = None,
    findings: List[String] = Nil,
    passages: List[EventPassage] = Nil,
    reasons: List[String] = Nil
) derives KindlingsCodecAsObject
object EventLinkCandidate:
  given Schema[EventLinkCandidate] = KindlingsSchema.derived[EventLinkCandidate].schema

final case class EventLinkList(eventId: String, label: String, candidates: List[EventLinkCandidate])
    derives KindlingsCodecAsObject
object EventLinkList:
  given Schema[EventLinkList] = KindlingsSchema.derived[EventLinkList].schema

/** `POST /api/events/{id}/links`: `recompute` the candidates; `confirm` sources (each becomes an
  * attestation from its best passage — refused for a source with no passage naming the event);
  * `reject` sources (they stop being proposed). */
final case class EventLinksRequest(recompute: Boolean = false, confirm: List[String] = Nil, reject: List[String] = Nil)
object EventLinksRequest:
  given Schema[EventLinksRequest] = KindlingsSchema.derived[EventLinksRequest].schema
  given Codec.AsObject[EventLinksRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[EventLinksRequest])

/** What a links call did: the candidates now, the attestations written, and the sources refused with
  * why. */
final case class EventLinksResult(
    links: EventLinkList,
    attested: List[Attestation] = Nil,
    refused: List[String] = Nil,
    rejected: List[String] = Nil
) derives KindlingsCodecAsObject
object EventLinksResult:
  given Schema[EventLinksResult] = KindlingsSchema.derived[EventLinksResult].schema

/** `POST /api/events/candidates` (a job): the candidates of every event — or the catalogue ones only,
  * or those not computed yet — so the cards and `events leads` read stored answers. */
final case class EventCandidatesRequest(all: Boolean = false, event: Option[String] = None, limit: Option[Int] = None)
    derives KindlingsCodecAsObject
object EventCandidatesRequest:
  given Schema[EventCandidatesRequest] = KindlingsSchema.derived[EventCandidatesRequest].schema

/** The matcher. Pure: the database gives it the findings near an event and the passages naming it. */
object EventMatcher:

  /** How far a finding may be from the event (`km`) and how many years apart (`years`; 0 = their spans
    * overlap), what a name match and a finding weigh, the least score kept, and the least length of a
    * name worth searching for. */
  final case class Weights(km: Double, years: Long, name: Double, finding: Double, minScore: Double,
      minNameChars: Int, maxCandidates: Int)

  /** One finding near the event: its id, source, distance and the years between its period and the
    * event's span (0 when they overlap). */
  final case class NearFinding(id: String, citekey: String, km: Double, yearGap: Long)

  /** Pure: the names worth searching a body for — distinct ignoring case, at least `minChars` long and
    * more than a bare kind word ("Battle", "Siege"), longest first (a longer name is the stronger match). */
  def searchNames(names: List[String], minChars: Int): List[String] =
    val generic = (EventTaxonomy.kinds.map(_.label) ++ EventTaxonomy.kinds.map(_.kind)).map(_.toLowerCase).toSet
    names.map(_.trim.replaceAll("\\s+", " ")).filter(n => n.length >= minChars && !generic(n.toLowerCase))
      .distinctBy(_.toLowerCase).sortBy(n => (-n.length, n))

  /** Pure: how much one finding says — 1 at the event's place and time, falling linearly to 0 at `km`
    * away and at `years` apart. */
  def findingWeight(f: NearFinding, w: Weights): Double =
    val space = if w.km <= 0 then 0.0 else (1.0 - f.km / w.km).max(0.0)
    val time  = if w.years <= 0 then (if f.yearGap == 0 then 1.0 else 0.0) else (1.0 - f.yearGap.toDouble / w.years).max(0.0)
    space * time

  /** Pure: the candidates for one event — every source with a finding near it or a passage naming it,
    * scored: `name` for a source whose text names the event (a little more for each further passage,
    * up to five), plus `finding` × the summed finding weights (at most `finding`). Sources below
    * `minScore` are dropped; the best `maxCandidates` kept, best first. */
  def candidates(eventId: String, near: List[NearFinding], named: Map[String, List[EventPassage]], w: Weights,
      titles: Map[String, String] = Map.empty): List[EventLinkCandidate] =
    val byCk = near.groupBy(_.citekey)
    (byCk.keySet ++ named.keySet).toList.flatMap { ck =>
      val fs = byCk.getOrElse(ck, Nil).sortBy(f => (f.yearGap, f.km, f.id))
      val ps = named.getOrElse(ck, Nil)
      val fScore = (fs.map(findingWeight(_, w)).sum * w.finding).min(w.finding)
      val nScore = if ps.isEmpty then 0.0 else w.name * (1.0 + 0.1 * (ps.size - 1).min(4))
      val score = BigDecimal(fScore + nScore).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
      val reasons =
        Option.when(ps.nonEmpty)(s"names it ${Plural.count(ps.size, "time")}: " +
          ps.map(p => s"\"${p.quote}\"").distinct.take(3).mkString(", ")).toList ++
        Option.when(fs.nonEmpty) {
          val nearest = fs.map(_.km).min
          val overlap = fs.count(_.yearGap == 0)
          s"${Plural.count(fs.size, "finding")} within ${math.round(fs.map(_.km).max)} km (nearest ${math.round(nearest)} km)" +
            (if overlap > 0 then s", $overlap overlapping in time" else s", nearest in time ${fs.map(_.yearGap).min} years apart")
        }.toList
      Option.when(score >= w.minScore)(EventLinkCandidate(eventId, ck, score, EventLinkStatus.Proposed,
        titles.get(ck), fs.map(_.id), ps, reasons))
    }.sortBy(c => (-c.score, c.citekey)).take(w.maxCandidates.max(1))

  /** Pure: the passage an attestation is written from — the longest name matched, the first place it
    * occurs. `None` when no passage names the event: then no attestation may be written. */
  def attestationPassage(ps: List[EventPassage]): Option[EventPassage] =
    ps.sortBy(p => (-p.name.length, p.start)).headOption

  /** Pure: the attestation a confirmed passage becomes — the quote and its offsets (W3C
    * TextQuoteSelector + TextPositionSelector), and a note saying how it was found. */
  def attestation(eventId: String, citekey: String, p: EventPassage): AttestationPut =
    AttestationPut(citekey, AttestationTarget.Event, eventId, quote = Some(p.quote),
      charStart = Some(p.start), charEnd = Some(p.end),
      note = Some(s"names the event as \"${p.name}\" (confirmed from `events links`)"))

// ── questions that guide research ───────────────────────────────────────────────────────────────

/** A command a lead can be acted on with: `discover` (search the literature for it), `acquire` (put it
  * on the acquire queue), `goal` (link it to a research goal), `confirm` (confirm a proposed source).
  * `command` is the CLI line; `query` the text the web UI hands the discover endpoint. */
final case class EventLeadAction(kind: String, label: String, command: String, query: Option[String] = None)
    derives KindlingsCodecAsObject
object EventLeadAction:
  given Schema[EventLeadAction] = KindlingsSchema.derived[EventLeadAction].schema

/** One question an event raises, in counts, dates and distances. */
final case class EventLead(
    eventId: String,
    label: String,
    kind: String,
    tier: String,
    years: String,
    text: String,
    actions: List[EventLeadAction] = Nil,
    findingsNear: Int = 0,
    candidates: Int = 0
) derives KindlingsCodecAsObject
object EventLead:
  given Schema[EventLead] = KindlingsSchema.derived[EventLead].schema

final case class EventLeadList(total: Int, leads: List[EventLead]) derives KindlingsCodecAsObject
object EventLeadList:
  given Schema[EventLeadList] = KindlingsSchema.derived[EventLeadList].schema

/** What a lead is computed from: the event, its tier, the catalogue's magnitude / VEI, the corpus's
  * findings near it (within `km` and `years`), its proposed (unconfirmed) sources and attesting ones. */
final case class LeadFacts(
    eventId: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    tier: String,
    vei: Option[Int],
    magnitude: Option[Double],
    findingsNear: Int,
    km: Double,
    years: Long,
    proposed: Int,
    attesting: Int)

object EventLeads:

  /** Pure: an event's years in words (`1630 BCE–1590 BCE`, or one year: [[YearFmt.span]]). */
  def years(ts: TimeSpan): String =
    YearFmt.span(ts)

  /** Pure: the size the catalogue gives (`VEI 7`, `M 7.3`), if any. */
  def size(vei: Option[Int], magnitude: Option[Double]): Option[String] =
    vei.map(v => s"VEI $v").orElse(magnitude.map(m => s"M ${BigDecimal(m).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))

  private def q(s: String): String = "\"" + s.replace("\"", "'") + "\""

  /** Pure: the commands that act on a lead about `label`. */
  def actions(eventId: String, label: String, yrs: String, proposed: Int): List[EventLeadAction] =
    List(
      EventLeadAction("discover", "search the literature", s"histmersion discover search ${q(label)} --not-in-db", Some(label)),
      EventLeadAction("acquire", "add to the acquire queue",
        s"histmersion queue acquire add ${q(s"literature on $label ($yrs)")} --topic events"),
      EventLeadAction("goal", "link to a goal", s"histmersion goals link <goal> --query ${q(label)}", Some(label))) ++
      Option.when(proposed > 0)(EventLeadAction("confirm", "review the proposed sources",
        s"histmersion events links $eventId")).toList

  /** Pure: the lead an event raises, if any. A CATALOGUE event no source attests: "No source in the
    * corpus discusses this …", with the findings around it counted and, when sources are proposed, how
    * many. An attested event raises none. */
  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != EventTier.Catalogue || f.attesting > 0 then None
    else
      val k     = EventTaxonomy.of(f.kind).label
      val yrs   = years(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around = s"${Plural.count(f.findingsNear, "finding")} in the corpus within ${math.round(f.km)} km and ±${f.years} years"
      val text =
        if f.proposed > 0 then
          s"No source in the corpus is confirmed to discuss this $sized ($yrs) — ${Plural.count(f.proposed, "source")} " +
            s"possibly discuss it (unconfirmed); $around"
        else s"No source in the corpus discusses this $sized ($yrs) — $around"
      Some(EventLead(f.eventId, f.label, EventTaxonomy.canonical(f.kind), f.tier, yrs, text,
        actions(f.eventId, f.label, yrs, f.proposed), f.findingsNear, f.proposed))

  /** Pure: Markdown for a list of leads (`histmersion events leads`, the MCP tool). */
  def markdown(l: EventLeadList): String =
    if l.leads.isEmpty then "_No leads: every event in the selection is attested by a source in the corpus, or none matched._"
    else
      (s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n")

/** A catalogue event an ARTICLE's period and region hold that its text never names — a lead listed on
  * the article's coverage, never inserted into its prose. */
final case class ArticleEventLead(eventId: String, label: String, kind: String, years: String, size: Option[String],
    findingsNear: Int, provider: String) derives KindlingsCodecAsObject
object ArticleEventLead:
  given Schema[ArticleEventLead] = KindlingsSchema.derived[ArticleEventLead].schema

  /** Pure: does an article body name the event (its label or another of its names, ignoring case)? */
  def mentioned(body: String, names: List[String]): Boolean =
    val b = body.toLowerCase(java.util.Locale.ROOT)
    names.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.length >= 4).exists(b.contains)

  /** Pure: the coverage section listing them. */
  def markdown(leads: List[ArticleEventLead]): String =
    if leads.isEmpty then ""
    else
      ("### Catalogue events in this article's period and region that it never names" :: "" ::
        leads.map { l =>
          s"- ${l.label} (`${l.eventId}`, ${l.kind}${l.size.fold("")(s => s", $s")}, ${l.years}) — " +
            s"${Plural.count(l.findingsNear, "finding")} of the filter's sources near it; from ${l.provider} (a lead, not evidence)"
        }).mkString("\n")

/** The catalogue half of an event's Markdown (`events show`, the `events` tool) and the links lists. */
object EventLinksMarkdown:

  private def km(d: Double): String = s"${math.round(d)} km"

  /** Pure: the tier, the catalogues (licence, citation), the attesting sources, the articles, the
    * findings near it, the proposed sources (unconfirmed) and the leads. Empty for an event with none. */
  def context(d: EventDetail): String =
    val tier = s"\n\n**Tier:** ${d.tier}" + (if d.tier == EventTier.Catalogue then
      " — known from a catalogue only: a research lead, not evidence" else "")
    val names = if d.names.isEmpty then "" else s"  \n**Also called:** ${d.names.take(12).mkString(", ")}"
    val cat = if d.catalogue.isEmpty then "" else
      "\n\n**Catalogues**\n" + d.catalogue.map { c =>
        val size = (c.vei.map(v => s"VEI $v") ++ c.magnitude.map(m => s"M $m") ++ c.deaths.map(n => s"$n deaths")).mkString(", ")
        s"- ${c.title} `${c.providerId}`${if size.isEmpty then "" else s" ($size)"} — ${c.url} · licence: ${c.licence} · cite: ${c.citation}"
      }.mkString("\n")
    val srcs = if d.sources.isEmpty then "" else
      "\n\n**Attested by**\n" + d.sources.map(s => s"- [[${s.citekey}]]${s.title.fold("")(t => s" — $t")}" +
        s.quote.fold("")(q => s""" ("$q")""")).mkString("\n")
    val arts = if d.articles.isEmpty then "" else
      "\n\n**Articles citing them**\n" + d.articles.map(a => s"- [[${a.slug}]] — ${a.title} (cites [[${a.citekey}]])").mkString("\n")
    val near = if d.findings.isEmpty then "" else
      "\n\n**Findings near it**\n" + d.findings.map(f => s"- [[${f.citekey}]] `${f.id}` — ${km(f.km)}, " +
        s"${if f.yearGap == 0 then "overlapping in time" else s"${f.yearGap} years apart"}: ${f.claim.take(140)}").mkString("\n")
    val cands = if d.candidates.isEmpty then "" else
      "\n\n**Possibly discussed in (unconfirmed)**\n" + d.candidates.map(line).mkString("\n")
    val leads = if d.leads.isEmpty then "" else
      "\n\n**Leads**\n" + d.leads.map(l => s"- ${l.text}\n" + l.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")).mkString("\n")
    tier + names + cat + srcs + arts + near + cands + leads

  private def line(c: EventLinkCandidate): String =
    s"- [[${c.citekey}]]${c.title.fold("")(t => s" — $t")} · score ${c.score} · ${c.status}" +
      (if c.reasons.isEmpty then "" else s" — ${c.reasons.mkString("; ")}")

  def list(l: EventLinkList): String =
    if l.candidates.isEmpty then s"_No source in the corpus is near `${l.eventId}` in time and space or names it._"
    else (s"## Sources that may discuss ${l.label} (`${l.eventId}`)" :: "" ::
      "_Proposed from findings near it and passages naming it — unconfirmed until confirmed (an attestation)._" :: "" ::
      l.candidates.map(line)).mkString("\n")

  def result(r: EventLinksResult): String =
    List(
      Option.when(r.attested.nonEmpty)("**Attested:** " + r.attested.map(a =>
        s"[[${a.citekey}]] #${a.id} (chars ${a.charStart.getOrElse(0)}–${a.charEnd.getOrElse(0)}: \"${a.quote.getOrElse("")}\")").mkString(", ")),
      Option.when(r.refused.nonEmpty)("**Not attested:**\n" + r.refused.map("- " + _).mkString("\n")),
      Option.when(r.rejected.nonEmpty)("**Rejected:** " + r.rejected.mkString(", ")),
      Some(list(r.links))
    ).flatten.mkString("\n\n")
