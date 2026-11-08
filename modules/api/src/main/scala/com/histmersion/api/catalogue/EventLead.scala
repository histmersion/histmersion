package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventLead:


  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Tier = Tier.T
  object Tier extends RefinedType[String, Pure]
  type Years = Years.T
  object Years extends RefinedType[String, Pure]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends RefinedType[Int, Pure]
  type Candidates = Candidates.T
  object Candidates extends RefinedType[Int, Pure]

  final case class Action(kind: Action.Kind, label: Action.Label, command: Action.Command, query: Option[Action.Query] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Action:
    type Kind = Kind.T
    object Kind extends RefinedType[String, Pure]
    type Label = Label.T
    object Label extends RefinedType[String, Pure]
    type Command = Command.T
    object Command extends RefinedType[String, Pure]
    type Query = Query.T
    object Query extends RefinedType[String, Pure]


  final case class List(total: List.Total, leads: scala.List[EventLead]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Total = Total.T
    object Total extends RefinedType[Int, Pure]
