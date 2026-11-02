package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Bucket(
    label: String,
    key: String,
    kind: String,
    start: Option[Long] = None,
    end: Option[Long] = None,
    placeId: Option[String] = None,
    count: Int = 0,
    overlap: Int = 0,
    contested: Boolean = false,
    alsoUnder: List[String] = Nil,
    children: List[Bucket] = Nil,
    samples: List[BucketSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Bucket:
  /** [[Bucket]] is recursive, so its schema is a `KindlingsSchema[Bucket]` (the `derives` clause), not a given
    * `Schema[Bucket]`: a typed `given` is visible in its own right-hand side, so the derivation would find it at the
    * `children` field and the lazy val would wait on itself forever on first use (tapir's own `Schema.derived` hangs
    * the same way). Ask for it with `Bucket.schema`; do not add a given `Schema[Bucket]` here.
    */
  def schema: Schema[Bucket] = summon[KindlingsSchema[Bucket]].schema
