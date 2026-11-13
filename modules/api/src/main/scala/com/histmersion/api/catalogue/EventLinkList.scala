package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventLinkList(eventId: Event.Id, label: EventLinkList.Label, candidates: List[EventLinkCandidate])
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object EventLinkList:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
