package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventLead(
    eventId: com.histmersion.api.catalogue.Event.Id,
    label: EventLead.Label,
    kind: EventLead.Kind,
    tier: EventLead.Tier,
    years: EventLead.Years,
    text: EventLead.Text,
    actions: scala.List[EventLead.Action] = Nil,
    findingsNear: EventLead.FindingsNear = _root_.com.histmersion.api.catalogue.EventLead.FindingsNear(0),
    candidates: EventLead.Candidates = _root_.com.histmersion.api.catalogue.EventLead.Candidates(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object EventLead:


  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tier = Tier.T
  object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Years = Years.T
  object Years extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FindingsNear = FindingsNear.T
  object FindingsNear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Candidates = Candidates.T
  object Candidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Action(kind: Action.Kind, label: Action.Label, command: Action.Command, query: Option[Action.Query] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Action:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Command = Command.T
    object Command extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class List(total: List.Total, leads: scala.List[EventLead]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object List:
    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
