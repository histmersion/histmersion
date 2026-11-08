package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class EventParticipant(
    eventId: Event.Id,
    actor: EventParticipant.Actor,
    role: EventParticipant.Role = EventParticipant.Role("participant"),
    citekey: Option[Citekey] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventParticipant:
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
