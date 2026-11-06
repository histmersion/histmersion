package com.histmersion.api.catalogue

import com.histmersion.api.{Plural, TimeSpan, YearFmt}
import com.histmersion.api.given
object EventLeads:
  type LeadList = LeadList.T
  object LeadList extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SizeVei = _root_.com.histmersion.api.catalogue.LeadFacts.Vei
  val SizeVei: _root_.com.histmersion.api.catalogue.LeadFacts.Vei.type = _root_.com.histmersion.api.catalogue.LeadFacts.Vei
  type SizeMagnitude = _root_.com.histmersion.api.catalogue.LeadFacts.Magnitude
  val SizeMagnitude: _root_.com.histmersion.api.catalogue.LeadFacts.Magnitude.type = _root_.com.histmersion.api.catalogue.LeadFacts.Magnitude
  type ActionsLabel = _root_.com.histmersion.api.catalogue.LeadFacts.Label
  val ActionsLabel: _root_.com.histmersion.api.catalogue.LeadFacts.Label.type = _root_.com.histmersion.api.catalogue.LeadFacts.Label
  type ActionsProposed = _root_.com.histmersion.api.catalogue.LeadFacts.Proposed
  val ActionsProposed: _root_.com.histmersion.api.catalogue.LeadFacts.Proposed.type = _root_.com.histmersion.api.catalogue.LeadFacts.Proposed

  def years(ts: TimeSpan): String =
    YearFmt.span(ts)

  def size(vei: Option[com.histmersion.api.catalogue.EventLeads.SizeVei], magnitude: Option[com.histmersion.api.catalogue.EventLeads.SizeMagnitude]): Option[String] =
    (vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal((m).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))).map(refactorValue2 => (refactorValue2))

  private def q(s: String): String = ("\"" + (s).replace("\"", "'") + "\"")

  def actions(eventId: com.histmersion.api.catalogue.Event.Id, label: com.histmersion.api.catalogue.EventLeads.ActionsLabel, yrs: String, proposed: com.histmersion.api.catalogue.EventLeads.ActionsProposed): List[EventLead.Action] =
    List(
      EventLead.Action(
        _root_.com.histmersion.api.catalogue.EventLead.Action.Kind("discover"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Label("search the literature"),
        s"histmersion discover search ${q(label)} --not-in-db",
        Some(label)
      ),
      EventLead.Action(
        _root_.com.histmersion.api.catalogue.EventLead.Action.Kind("acquire"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Label("add to the acquire queue"),
        _root_.com.histmersion.api.catalogue.EventLead.Action.Command(s"histmersion queue acquire add ${q((s"literature on $label ($yrs)"))} --topic events")
      ),
      EventLead.Action(_root_.com.histmersion.api.catalogue.EventLead.Action.Kind("goal"), _root_.com.histmersion.api.catalogue.EventLead.Action.Label("link to a goal"), s"histmersion goals link <goal> --query ${q(label)}", Some(label))
    ) ++
      Option
        .when(proposed > 0)(
          EventLead.Action("confirm", "review the proposed sources", s"histmersion events links $eventId")
        )
        .toList

  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != _root_.com.histmersion.api.catalogue.LeadFacts.Tier(EventTier.Catalogue) || (f.attesting).value > 0 then None
    else
      val k = EventTaxonomy.of(f.kind).label
      val yrs = years(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around =
        s"${Plural.count(f.findingsNear, _root_.com.histmersion.api.Plural.CountSingular("finding"))} in the corpus within ${math.round(f.km)} km and ±${f.years} years"
      val text =
        if (f.proposed).value > 0 then
          s"No source in the corpus is confirmed to discuss this $sized ($yrs) — ${Plural.count(f.proposed, _root_.com.histmersion.api.Plural.CountSingular("source"))} " +
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
          actions(f.eventId, f.label, ((yrs)), f.proposed),
          f.findingsNear,
          f.proposed
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
