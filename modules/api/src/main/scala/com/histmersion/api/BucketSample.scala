package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── wire DTOs ───────────────────────────────────────────────────────────────────────────────────
// Declared LAST in the file (and `Bucket`, which references `BucketSample`, last of all): the
// native-image metadata indexes the derived lazies per compilation unit.

/** One sample source under a leaf bucket — enough to decide whether to open it, no more. */
final case class BucketSample(citekey: String, title: String) derives KindlingsCodecAsObject, KindlingsSchema
object BucketSample:
  given Schema[BucketSample] = summon[KindlingsSchema[BucketSample]].schema
