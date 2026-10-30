package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BundleReference(places: Int, placesDigest: String, periods: Int, periodsDigest: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleReference:
  given Schema[BundleReference] = summon[KindlingsSchema[BundleReference]].schema
