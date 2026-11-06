package com.histmersion.api.catalogue

import com.histmersion.api.{Plural, TimeSpan, YearFmt}
import com.histmersion.api.given
object EventLeads:

  type YearsResult = YearsResult.T
  object YearsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeResult = SizeResult.T
  object SizeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeVei = SizeVei.T
  object SizeVei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SizeMagnitude = SizeMagnitude.T
  object SizeMagnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QResult = QResult.T
  object QResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QS = QS.T
  object QS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionsEventId = ActionsEventId.T
  object ActionsEventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionsLabel = ActionsLabel.T
  object ActionsLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionsYrs = ActionsYrs.T
  object ActionsYrs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionsProposed = ActionsProposed.T
  object ActionsProposed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def years(ts: TimeSpan): com.histmersion.api.catalogue.EventLeads.YearsResult =
    YearFmt.span(ts)

  def size(vei: Option[com.histmersion.api.catalogue.EventLeads.SizeVei], magnitude: Option[com.histmersion.api.catalogue.EventLeads.SizeMagnitude]): Option[com.histmersion.api.catalogue.EventLeads.SizeResult] =
    vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal(m).setScale(1, BigDecimal.RoundingMode.HALF_UP)}"))

  private def q(s: com.histmersion.api.catalogue.EventLeads.QS): com.histmersion.api.catalogue.EventLeads.QResult = "\"" + s.replace("\"", "'") + "\""

  def actions(eventId: com.histmersion.api.catalogue.EventLeads.ActionsEventId, label: com.histmersion.api.catalogue.EventLeads.ActionsLabel, yrs: com.histmersion.api.catalogue.EventLeads.ActionsYrs, proposed: com.histmersion.api.catalogue.EventLeads.ActionsProposed): List[EventLead.Action] =
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
        s"histmersion queue acquire add ${q(_root_.com.histmersion.api.catalogue.EventLeads.QS(s"literature on $label ($yrs)"))} --topic events"
      ),
      EventLead.Action(_root_.com.histmersion.api.catalogue.EventLead.Action.Kind("goal"), _root_.com.histmersion.api.catalogue.EventLead.Action.Label("link to a goal"), s"histmersion goals link <goal> --query ${q(label)}", Some(label))
    ) ++
      Option
        .when(proposed > 0)(
          EventLead.Action("confirm", "review the proposed sources", s"histmersion events links $eventId")
        )
        .toList

  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != EventTier.Catalogue || f.attesting > 0 then None
    else
      val k = EventTaxonomy.of(f.kind).label
      val yrs = years(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around =
        s"${Plural.count(f.findingsNear, _root_.com.histmersion.api.Plural.CountSingular("finding"))} in the corpus within ${math.round(f.km)} km and ±${f.years} years"
      val text =
        if f.proposed > 0 then
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
          actions(f.eventId, f.label, yrs, f.proposed),
          f.findingsNear,
          f.proposed
        )
      )

  def markdown(l: EventLead.List): com.histmersion.api.catalogue.EventLeads.MarkdownResult =
    if l.leads.isEmpty then
      _root_.com.histmersion.api.catalogue.EventLeads.MarkdownResult("_No leads: every event in the selection is attested by a source in the corpus, or none matched._")
    else
      _root_.com.histmersion.api.catalogue.EventLeads.MarkdownResult((s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n"))
