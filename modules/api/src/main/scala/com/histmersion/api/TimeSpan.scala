package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeSpan(
    notBefore: Option[com.histmersion.api.catalogue.Year] = None,
    beginBy: Option[com.histmersion.api.catalogue.Year] = None,
    endFrom: Option[com.histmersion.api.catalogue.Year] = None,
    notAfter: Option[com.histmersion.api.catalogue.Year] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:

  /** The outer span: the earliest and latest years stated, one known end standing for both; `None` when undated. */
  def outer: Option[(com.histmersion.api.TimeSpan.OuterResultPart1, com.histmersion.api.TimeSpan.OuterResultPart2)] =
    val start = notBefore.orElse(beginBy).orElse(endFrom).orElse(notAfter)
    val end = notAfter.orElse(endFrom).orElse(beginBy).orElse(notBefore)
    for a <- start; b <- end yield ((a).value.min(b), (a).value.max(b))

object TimeSpan:
  type OuterResultPart1 = OuterResultPart1.T
  object OuterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OuterResultPart2 = OuterResultPart2.T
  object OuterResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
