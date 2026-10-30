package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The whole `/api/aggregate` answer: the window actually aggregated, the adaptive bucket size chosen for it, and —
  * depending on `by` — the flat strip and/or the nested tree.
  *
  *   - `by=time` (plan §1.7, the default) fills `buckets`: one bar per time bucket, gaps included, which is what the
  *     timeline strip draws.
  *   - `by=region|time,region|region,time` (plan §1.6) fills `tree`: the nested [[Bucket]] forest, empty buckets
  *     pruned, `depth` levels deep, the gazetteer read as of `asOf` (for `by=region`; `time,region` reads it at each
  *     time bucket's own midpoint and leaves `asOf` empty).
  *
  * Every count in both is `count(DISTINCT unit)` and a unit is counted in EVERY bucket it overlaps, so nothing here
  * sums to `total` — see [[Bucket.overlap]].
  */
final case class TimeHistogram(
    from: Long,
    to: Long,
    bucket: Long,
    buckets: List[TimeBucket],
    // ── the §1.6 tree. Defaults keep the wave-1 `by=time` JSON byte-identical for old clients. ──
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
