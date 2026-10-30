package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class GoalDeleted(
    slug: String,
    deleted: Boolean
) derives KindlingsCodecAsObject,
      KindlingsSchema

object GoalDeleted:
  given Schema[GoalDeleted] = summon[KindlingsSchema[GoalDeleted]].schema
