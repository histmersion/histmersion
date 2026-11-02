package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BundleBlobs(count: Int, bytes: Long) derives KindlingsCodecAsObject, KindlingsSchema
object BundleBlobs:
  given Schema[BundleBlobs] = summon[KindlingsSchema[BundleBlobs]].schema
