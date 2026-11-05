package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventParticipant(
    eventId: com.histmersion.api.catalogue.Event.Id,
    actor: EventParticipant.Actor,
    role: EventParticipant.Role = "participant",
    citekey: Option[com.histmersion.api.library.Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventParticipant:
  type Actor = Actor.T
  object Actor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Role = Role.T
  object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
