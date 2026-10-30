package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BundleUpload(sha256: String, bytes: Long, manifest: Option[BundleManifest], problems: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleUpload:
  given Schema[BundleUpload] = summon[KindlingsSchema[BundleUpload]].schema
