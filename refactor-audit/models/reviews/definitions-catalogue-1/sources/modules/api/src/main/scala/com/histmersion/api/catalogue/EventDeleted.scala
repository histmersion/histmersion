package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventDeleted(id: com.histmersion.api.catalogue.Event.Id, deleted: EventDeleted.Deleted) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object EventDeleted:
  type Deleted = Deleted.T
  object Deleted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
