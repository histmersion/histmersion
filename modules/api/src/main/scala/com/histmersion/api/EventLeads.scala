package com.histmersion.api

/** Linking events to the corpus (#1449), deterministically — no model: the sources whose FINDINGS lie near an event in
  * time and space, and the sources whose TEXT names it (any of its names — its label, the catalogue's other labels and
  * aliases in other languages). Such a source is a PROPOSED link, kept with its evidence (the finding ids, the passages
  * with their character offsets) and a score; it is shown as "possibly discussed in", unconfirmed. Confirming one
  * writes an ATTESTATION — and only from a passage that names the event: a source no passage of which names it is never
  * attested by this path, however close its findings are.
  *
  * And the QUESTIONS an event raises — a catalogue event no source discusses, with the corpus's findings around it
  * counted — as factual leads with the commands that act on them.
  */

object EventLeads:

  /** Pure: an event's years in words (`1630 BCE–1590 BCE`, or one year: [[YearFmt.span]]). */
  def years(ts: TimeSpan): String =
    YearFmt.span(ts)

  /** Pure: the size the catalogue gives (`VEI 7`, `M 7.3`), if any. */
  def size(vei: Option[Int], magnitude: Option[Double]): Option[String] =
    vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal(m).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))

  private def q(s: String): String = "\"" + s.replace("\"", "'") + "\""

  /** Pure: the commands that act on a lead about `label`. */
  def actions(eventId: String, label: String, yrs: String, proposed: Int): List[EventLeadAction] =
    List(
      EventLeadAction(
        "discover",
        "search the literature",
        s"histmersion discover search ${q(label)} --not-in-db",
        Some(label)
      ),
      EventLeadAction(
        "acquire",
        "add to the acquire queue",
        s"histmersion queue acquire add ${q(s"literature on $label ($yrs)")} --topic events"
      ),
      EventLeadAction("goal", "link to a goal", s"histmersion goals link <goal> --query ${q(label)}", Some(label))
    ) ++
      Option
        .when(proposed > 0)(
          EventLeadAction("confirm", "review the proposed sources", s"histmersion events links $eventId")
        )
        .toList

  /** Pure: the lead an event raises, if any. A CATALOGUE event no source attests: "No source in the corpus discusses
    * this …", with the findings around it counted and, when sources are proposed, how many. An attested event raises
    * none.
    */
  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != EventTier.Catalogue || f.attesting > 0 then None
    else
      val k = EventTaxonomy.of(f.kind).label
      val yrs = years(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around =
        s"${Plural.count(f.findingsNear, "finding")} in the corpus within ${math.round(f.km)} km and ±${f.years} years"
      val text =
        if f.proposed > 0 then
          s"No source in the corpus is confirmed to discuss this $sized ($yrs) — ${Plural.count(f.proposed, "source")} " +
            s"possibly discuss it (unconfirmed); $around"
        else s"No source in the corpus discusses this $sized ($yrs) — $around"
      Some(
        EventLead(
          f.eventId,
          f.label,
          EventTaxonomy.canonical(f.kind),
          f.tier,
          yrs,
          text,
          actions(f.eventId, f.label, yrs, f.proposed),
          f.findingsNear,
          f.proposed
        )
      )

  /** Pure: Markdown for a list of leads (`histmersion events leads`, the MCP tool). */
  def markdown(l: EventLeadList): String =
    if l.leads.isEmpty then
      "_No leads: every event in the selection is attested by a source in the corpus, or none matched._"
    else
      (s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n")
