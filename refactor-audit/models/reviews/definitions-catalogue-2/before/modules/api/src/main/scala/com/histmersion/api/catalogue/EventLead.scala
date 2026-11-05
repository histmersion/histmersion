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
      KindlingsSchema, com.histmersion.api.json.Schema
object EventLead:


  final case class Action(kind: String, label: String, command: String, query: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema


  final case class List(total: Int, leads: scala.List[EventLead]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
