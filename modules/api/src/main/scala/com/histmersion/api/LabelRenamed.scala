package com.histmersion.api

import com.histmersion.values.BundleKind

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class LabelRenamed(entity: BundleKind, from: String, to: String, rewritten: List[String] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object LabelRenamed:
  given Schema[LabelRenamed] = summon[KindlingsSchema[LabelRenamed]].schema
