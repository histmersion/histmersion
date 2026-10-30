package com.histmersion.api

object EventLeads:

  def years(ts: TimeSpan): String =
    YearFmt.span(ts)

  def size(vei: Option[Int], magnitude: Option[Double]): Option[String] =
    vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal(m).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))

  private def q(s: String): String = "\"" + s.replace("\"", "'") + "\""

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

  def markdown(l: EventLeadList): String =
    if l.leads.isEmpty then
      "_No leads: every event in the selection is attested by a source in the corpus, or none matched._"
    else
      (s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n")
