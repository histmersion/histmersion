package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventDeleted(id: Event.Id, deleted: EventDeleted.Deleted)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object EventDeleted:
  type Deleted = Deleted.T
  object Deleted extends Newtype[Boolean, Pure]
