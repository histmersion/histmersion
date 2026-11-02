package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BucketSample(citekey: String, title: String) derives KindlingsCodecAsObject, KindlingsSchema
object BucketSample:
  given Schema[BucketSample] = summon[KindlingsSchema[BucketSample]].schema
