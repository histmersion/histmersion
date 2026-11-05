package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventCandidatesRequest(all: EventCandidatesRequest.All = false, event: Option[com.histmersion.api.catalogue.Event.Id] = None, limit: Option[EventCandidatesRequest.Limit] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventCandidatesRequest:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
