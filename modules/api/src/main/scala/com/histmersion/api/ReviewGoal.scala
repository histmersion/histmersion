package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A research goal that links the article, with the goal's stage. */
final case class ReviewGoal(slug: String, title: String, status: String) derives KindlingsCodecAsObject, KindlingsSchema
object ReviewGoal:
  given Schema[ReviewGoal] = summon[KindlingsSchema[ReviewGoal]].schema
