package com.histmersion.api.aliases

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Bundle

final case class LabelRenamed(entity: Bundle.Kind, from: String, to: String, rewritten: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object LabelRenamed:
  given Schema[LabelRenamed] = summon[KindlingsSchema[LabelRenamed]].schema
