package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── DTOs ───────────────────────────────────────────────────────────────────────────────────────

/** Four-point time-span: uncertain bounds bracket the certain core. */
final case class TimeSpan(
    notBefore: Option[Long] = None,
    beginBy: Option[Long] = None,
    endFrom: Option[Long] = None,
    notAfter: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object TimeSpan:
  given Schema[TimeSpan] = summon[KindlingsSchema[TimeSpan]].schema
