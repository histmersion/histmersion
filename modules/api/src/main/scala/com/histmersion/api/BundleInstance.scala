package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

final case class BundleInstance(id: UUID, label: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
object BundleInstance:
  given Schema[BundleInstance] = summon[KindlingsSchema[BundleInstance]].schema
