package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

final case class BundleInstance(id: UUID, label: Option[String] = None) derives KindlingsCodecAsObject, KindlingsSchema
object BundleInstance:
  given Schema[BundleInstance] = summon[KindlingsSchema[BundleInstance]].schema
