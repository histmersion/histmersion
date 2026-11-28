package com.histmersion
package api.catalogue

import com.histmersion.api.TimeSpan
import com.histmersion.api.Plural
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EventLead(
    eventId: Event.Id,
    label: EventLead.Label,
    kind: EventLead.Kind,
    tier: EventLead.Tier,
    years: EventLead.Years,
    text: EventLead.Text,
    actions: scala.List[EventLead.Action] = Nil,
    findingsNear: EventLead.FindingsNear = EventLead.FindingsNear(0),
    candidates: EventLead.Candidates = EventLead.Candidates(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object EventLead:

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  export EventSummary.Kind
  type Tier = Tier.T
  object Tier extends Newtype[String, Pure]
  export ArticleEventLead.Years
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends Newtype[Int, Pure]
  type Candidates = Candidates.T
  object Candidates extends Newtype[Int, Pure]

  final case class Action(
      kind: Action.Kind,
      label: Action.Label,
      command: Action.Command,
      query: Option[Action.Query] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Action:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Command = Command.T
    object Command extends Newtype[String, Pure]
    type Query = Query.T
    object Query extends Newtype[String, Pure]

  final case class List(total: List.Total, leads: scala.List[EventLead])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object List:
    type Total = Total.T
    object Total extends Newtype[Int, Pure]

  type LeadList = LeadList.T
  object LeadList extends Newtype[String, Pure]

  def yearsOf(ts: TimeSpan): Years =
    Years(ts.show)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def size(vei: Option[LeadFacts.Vei], magnitude: Option[LeadFacts.Magnitude]): Option[String] =
    (vei
      .map(v => s"VEI $v")
      .orElse(magnitude.map(m => s"M ${BigDecimal((m).value).setScale(1, BigDecimal.RoundingMode.HALF_UP)}")))
      .map(refactorValue2 => (refactorValue2))

  private def q(s: String): String = ("\"" + (s).replace("\"", "'") + "\"")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def actionsFor(
      eventId: Event.Id,
      label: Label,
      yrs: Years,
      proposed: LeadFacts.Proposed
  ): scala.List[Action] =
    scala.List(
      Action(
        Action.Kind("discover"),
        Action.Label("search the literature"),
        Action.Command(s"histmersion discover search ${q(label.value)} --not-in-db"),
        Some(Action.Query(label.value))
      ),
      Action(
        Action.Kind("acquire"),
        Action.Label("add to the acquire queue"),
        Action.Command(
          s"histmersion queue acquire add ${q((s"literature on ${label.value} ($yrs)"))} --topic events"
        )
      ),
      Action(
        Action.Kind("goal"),
        Action.Label("link to a goal"),
        Action.Command(s"histmersion goals link <goal> --query ${q(label.value)}"),
        Some(Action.Query(label.value))
      )
    ) ++
      Option
        .when(proposed.value > 0)(
          Action(
            Action.Kind("confirm"),
            Action.Label("review the proposed sources"),
            Action.Command(s"histmersion events links $eventId")
          )
        )
        .toList

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(f: LeadFacts): Option[EventLead] =
    if f.tier != LeadFacts.Tier(EventTier.Catalogue) || (f.attesting).value > 0 then None
    else
      val kind = f.kind
      val label = Label(f.label.value)
      val k = EventTaxonomy.of(kind).label.value
      val yrs = yearsOf(f.timeSpan)
      val sized = size(f.vei, f.magnitude).fold(k)(s => s"$s $k")
      val around =
        s"${Plural.count(Plural.CountN(f.findingsNear.value.toLong), Plural.CountSingular("finding"))} in the corpus within ${math.round(f.km.value)} km and ±${f.years} years"
      val text =
        if (f.proposed).value > 0 then
          s"No source in the corpus is confirmed to discuss this $sized ($yrs) — ${Plural.count(Plural.CountN(f.proposed.value.toLong), Plural.CountSingular("source"))} " +
            s"possibly discuss it (unconfirmed); $around"
        else s"No source in the corpus discusses this $sized ($yrs) — $around"
      Some(
        EventLead(
          f.eventId,
          label,
          Kind(EventTaxonomy.canonical(kind).value),
          f.tier,
          yrs,
          Text(text),
          actionsFor(f.eventId, label, yrs, f.proposed),
          f.findingsNear,
          Candidates(f.proposed.value)
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(l: List): LeadList =
    if l.leads.isEmpty then
      LeadList("_No leads: every event in the selection is attested by a source in the corpus, or none matched._")
    else
      LeadList((s"## Event leads (${l.leads.size} of ${l.total})" :: "" :: l.leads.map { e =>
        s"- **${e.label}** (`${e.eventId}`, ${e.kind}) — ${e.text}\n" +
          e.actions.map(a => s"  - ${a.label}: `${a.command}`").mkString("\n")
      }).mkString("\n"))
