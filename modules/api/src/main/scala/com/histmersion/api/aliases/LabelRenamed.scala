package com.histmersion.api.aliases
import com.histmersion.values.BundleKind

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class LabelRenamed(entity: BundleKind, from: String, to: String, rewritten: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object LabelRenamed:
  given Schema[LabelRenamed] = summon[KindlingsSchema[LabelRenamed]].schema
