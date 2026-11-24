package com.histmersion
package plugins.events

import com.histmersion.plugin.{CatalogueEvent, Year}
import com.histmersion.upstreams.dating.CatalogDating
import com.histmersion.lint.{LintRule, tolerated}

/** An event's dating as the SPI states it: the four points of a span, each held to what a `Year` holds. */
final case class Dated(notBefore: Option[Year], beginBy: Option[Year], endFrom: Option[Year], notAfter: Option[Year])

object Dated:
  def of(s: CatalogDating.Span): Dated =
    Dated(
      s.notBefore.flatMap(year),
      s.beginBy.flatMap(year),
      s.endFrom.flatMap(year),
      s.notAfter.flatMap(year)
    )

  /** A year as the SPI states it; `None` beyond what a `Year` holds. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def year(y: CatalogDating.HistoricalYear): Option[Year] = Option.when(y.value.isValidInt)(Year(y.value.toInt))

  /** The span an event states, as the dating rules read it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def span(e: CatalogueEvent): CatalogDating.Span =
    CatalogDating.Span(
      e.notBefore.map(y => CatalogDating.HistoricalYear(y.value.toLong)),
      e.beginBy.map(y => CatalogDating.HistoricalYear(y.value.toLong)),
      e.endFrom.map(y => CatalogDating.HistoricalYear(y.value.toLong)),
      e.notAfter.map(y => CatalogDating.HistoricalYear(y.value.toLong))
    )
