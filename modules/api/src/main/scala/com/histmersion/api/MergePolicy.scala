package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.values.EditionsPolicy

final case class MergePolicy(editions: Option[EditionsPolicy] = None) derives KindlingsCodecAsObject, KindlingsSchema
object MergePolicy:
  given Schema[MergePolicy] = summon[KindlingsSchema[MergePolicy]].schema
