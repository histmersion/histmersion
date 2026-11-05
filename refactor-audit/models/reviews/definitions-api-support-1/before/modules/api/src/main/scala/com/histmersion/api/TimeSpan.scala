package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeSpan(
    notBefore: Option[Long] = None,
    beginBy: Option[Long] = None,
    endFrom: Option[Long] = None,
    notAfter: Option[Long] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The outer span: the earliest and latest years stated, one known end standing for both; `None` when undated. */
  def outer: Option[(Long, Long)] =
    val start = notBefore.orElse(beginBy).orElse(endFrom).orElse(notAfter)
    val end = notAfter.orElse(endFrom).orElse(beginBy).orElse(notBefore)
    for a <- start; b <- end yield (a.min(b), a.max(b))
