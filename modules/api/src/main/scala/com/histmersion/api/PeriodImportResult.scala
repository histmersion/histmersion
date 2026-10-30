package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What a period import wrote: rows upserted (authority nodes included), how many of those were authority nodes, the
  * timeline presets now in the store, the definitions NOT imported — how many (`skippedCount`) and, up to a limit,
  * which and why (`skipped`) — and, for `periods seed --replace`, how many earlier seed rows the seed no longer has
  * were deleted (`removed`).
  */
final case class PeriodImportResult(
    periods: Int,
    roots: Int = 0,
    presets: Int = 0,
    skipped: List[SkippedPeriod] = Nil,
    skippedCount: Int = 0,
    removed: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object PeriodImportResult:
  given Schema[PeriodImportResult] = summon[KindlingsSchema[PeriodImportResult]].schema
