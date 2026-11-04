package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.given

final case class MergePolicy(editions: Option[EditionsPolicy] = None) derives KindlingsCodecAsObject, KindlingsSchema
object MergePolicy:
  given Schema[MergePolicy] = summon[KindlingsSchema[MergePolicy]].schema
