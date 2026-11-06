package com.histmersion.api.catalogue

import com.histmersion.api.{Plural, TimeSpan, YearFmt}
import com.histmersion.api.given
object EventLeads:
  type LeadList = LeadList.T
  object LeadList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def years(ts: TimeSpan): EventLead.Years =
    EventLead.Years(YearFmt.span(ts))

  def size(vei: Option[com.histmersion.api.catalogue.LeadFacts.Vei], magnitude: Option[com.histmersion.api.catalogue.LeadFacts.Magnitude]): Option[String] =
    (vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal((m).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))).map(refactorValue2 => (refactorValue2))

  private def q(s: String): String = ("\"" + (s).replace("\"", "'") + "\"")

  def actions(eventId: com.histmersion.api.catalogue.Event.Id, label: EventLead.Label, yrs: EventLead.Years, proposed: com.histmersion.api.catalogue.LeadFacts.Proposed): List[EventLead.Action] =
    List(
      EventLead.Action(
        _root_.com.histmersion.api.catalogue.EventLead.Action.Kind("discover"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Label("search the literature"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Command(s"histmersion discover search ${q(label.value)} --not-in-db"),
        Some(_root_.com.histmersion.api.catalogue.EventLead.Action.Query(label.value))
      ),
      EventLead.Action(
        _root_.com.histmersion.api.catalogue.EventLead.Action.Kind("acquire"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Label("add to the acquire queue"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Command(s"histmersion queue acquire add ${q((s"literature on ${label.value} ($yrs)"))} --topic events")
      ),
      EventLead.Action(_root_.com.histmersion.api.catalogue.EventLead.Action.Kind("goal"), _root_.com.histmersion.api.catalogue.EventLead.Action.Label("link to a goal"), _root_.com.histmersion.api.catalogue.EventLead.Action.Command(s"histmersion goals link <goal> --query ${q(label.value)}"), Some(_root_.com.histmersion.api.catalogue.EventLead.Action.Query(label.value)))
    ) ++
      Option
        .when(proposed.value > 0)(
          EventLead.Action(_root_.com.histmersion.api.catalogue.EventLead.Action.Kind("confirm"), _root_.com.histmersion.api.catalogue.EventLead.Action.Label("review the proposed sources"), _root_.com.histmersion.api.catalogue.EventLead.Action.Command(s"histmersion events links $eventId"))
        )
        .toList

  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != _root_.com.histmersion.api.catalogue.LeadFacts.Tier(EventTier.Catalogue) || (f.attesting).value > 0 then None
    else
      val kind = _root_.com.histmersion.api.catalogue.EventSummary.Kind(f.kind.value)
      val label = EventLead.Label(f.label.value)
      val k = EventTaxonomy.of(kind).label.value
      val yrs = years(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around =
        s"${Plural.count(_root_.com.histmersion.api.Plural.CountN(f.findingsNear.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("finding"))} in the corpus within ${math.round(f.km.value)} km and ±${f.years} years"
      val text =
        if (f.proposed).value > 0 then
          s"No source in the corpus is confirmed to discuss this $sized ($yrs) — ${Plural.count(_root_.com.histmersion.api.Plural.CountN(f.proposed.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("source"))} " +
            s"possibly discuss it (unconfirmed); $around"
        else s"No source in the corpus discusses this $sized ($yrs) — $around"
      Some(
        EventLead(
          f.eventId,
          label,
          EventLead.Kind(EventTaxonomy.canonical(kind).value),
          EventLead.Tier(f.tier.value),
          yrs,
          EventLead.Text(text),
          actions(f.eventId, label, yrs, f.proposed),
          EventLead.FindingsNear(f.findingsNear.value),
          EventLead.Candidates(f.proposed.value)
        )
      )

  def markdown(l: EventLead.List): com.histmersion.api.catalogue.EventLeads.LeadList =
    if l.leads.isEmpty then
      _root_.com.histmersion.api.catalogue.EventLeads.LeadList("_No leads: every event in the selection is attested by a source in the corpus, or none matched._")
    else
      _root_.com.histmersion.api.catalogue.EventLeads.LeadList((s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n"))
