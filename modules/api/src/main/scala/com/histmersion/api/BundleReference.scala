package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The reference data a bundle does NOT carry, fingerprinted: the gazetteer and the named periods come from the same
  * bundled seeds on every instance (`histmersion init`), so events point into them by id. A differing fingerprint is
  * reported on import, never fatal.
  */
final case class BundleReference(places: Int, placesDigest: String, periods: Int, periodsDigest: String)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleReference:
  given Schema[BundleReference] = summon[KindlingsSchema[BundleReference]].schema
