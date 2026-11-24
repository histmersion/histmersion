package com.histmersion
package api

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@movesTo(FeaturePlace.Kernel)
final case class TimeSpan(
    notBefore: Option[Year] = None,
    beginBy: Option[Year] = None,
    endFrom: Option[Year] = None,
    notAfter: Option[Year] = None
) derives JsonCodec,
      KindlingsSchema,
      json.Schema,
      CanEqual:

  /** The outer span: the earliest and latest years stated, one known end standing for both; `None` when undated. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def outer: Option[(Year, Year)] =
    val start = notBefore.orElse(beginBy).orElse(endFrom).orElse(notAfter)
    val end = notAfter.orElse(endFrom).orElse(beginBy).orElse(notBefore)
    for a <- start; b <- end yield (Year(a.value.min(b.value)), Year(a.value.max(b.value)))

object TimeSpan
