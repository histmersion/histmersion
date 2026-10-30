package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Body of `POST /api/articles/{slug}/rename`, `/api/events/{id}/rename`, `/api/goals/{slug}/rename`,
  * `/api/places/{id}/rename`: the new label.
  */
final case class LabelRename(to: String) derives KindlingsCodecAsObject, KindlingsSchema
object LabelRename:
  given Schema[LabelRename] = summon[KindlingsSchema[LabelRename]].schema
