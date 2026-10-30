package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class Goal(
    slug: String,
    title: String,
    status: String,
    notes: Option[String] = None,
    tags: List[String] = Nil,
    regions: List[String] = Nil,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    parentSlug: Option[String] = None,
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object Goal:
  given Schema[Goal] = summon[KindlingsSchema[Goal]].schema
