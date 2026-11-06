package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

final case class TimeSpan(
    notBefore: Option[com.histmersion.api.catalogue.Year] = None,
    beginBy: Option[com.histmersion.api.catalogue.Year] = None,
    endFrom: Option[com.histmersion.api.catalogue.Year] = None,
    notAfter: Option[com.histmersion.api.catalogue.Year] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The outer span: the earliest and latest years stated, one known end standing for both; `None` when undated. */
  def outer: Option[(catalogue.Year, catalogue.Year)] =
    val start = notBefore.orElse(beginBy).orElse(endFrom).orElse(notAfter)
    val end = notAfter.orElse(endFrom).orElse(beginBy).orElse(notBefore)
    for a <- start; b <- end yield (catalogue.Year(a.value.min(b.value)), catalogue.Year(a.value.max(b.value)))

object TimeSpan
