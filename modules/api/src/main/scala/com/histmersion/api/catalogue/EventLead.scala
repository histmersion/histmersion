package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventLead(
    eventId: String,
    label: String,
    kind: String,
    tier: String,
    years: String,
    text: String,
    actions: scala.List[EventLead.Action] = Nil,
    findingsNear: Int = 0,
    candidates: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventLead:
  given Schema[EventLead] = summon[KindlingsSchema[EventLead]].schema

  final case class Action(kind: String, label: String, command: String, query: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Action:
    given Schema[Action] = summon[KindlingsSchema[Action]].schema

  final case class List(total: Int, leads: scala.List[EventLead]) derives KindlingsCodecAsObject, KindlingsSchema
  object List:
    given Schema[List] = summon[KindlingsSchema[List]].schema
