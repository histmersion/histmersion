package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class TimeHistogram(
    from: Long,
    to: Long,
    bucket: Long,
    buckets: List[TimeBucket],
    // Defaults keep the `by=time` JSON byte-identical for older clients.
    by: String = "time",
    unit: String = "sources",
    total: Int = 0,
    depth: Int = 0,
    asOf: Option[Long] = None,
    tree: List[Bucket] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object TimeHistogram:
  given Schema[TimeHistogram] = summon[KindlingsSchema[TimeHistogram]].schema
