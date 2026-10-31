package com.histmersion.plugins.events

import com.histmersion.plugin.{CatalogueEvent, Year}
import com.histmersion.upstreams.CatalogDating

/** An event's dating as the SPI states it: the four points of a span, each held to what a `Year` holds. */
final case class Dated(notBefore: Option[Year], beginBy: Option[Year], endFrom: Option[Year], notAfter: Option[Year])

object Dated:
  def of(s: CatalogDating.Span): Dated =
    Dated(
      s.notBefore.flatMap(JsonFields.year),
      s.beginBy.flatMap(JsonFields.year),
      s.endFrom.flatMap(JsonFields.year),
      s.notAfter.flatMap(JsonFields.year)
    )

  /** The span an event states, as the dating rules read it. */
  def span(e: CatalogueEvent): CatalogDating.Span =
    CatalogDating.Span(
      e.notBefore.map(_.value.toLong),
      e.beginBy.map(_.value.toLong),
      e.endFrom.map(_.value.toLong),
      e.notAfter.map(_.value.toLong)
    )
