package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Events from catalogues (#1449): `events` in reference.conf. The HTTP politeness (User-Agent, retries, Retry-After
  * cap) is `media`'s; each catalogue has its own pace.
  */
final case class EventsConfig(
    timeoutSeconds: EventsConfig.TimeoutSeconds,
    minYear: EventsConfig.MinYear,
    maxYear: EventsConfig.MaxYear,
    region: EventsConfig.Region,
    mergeKm: EventsConfig.MergeKm,
    mergeYears: EventsConfig.MergeYears,
    defaultLimit: EventsConfig.DefaultLimit,
    /** Records `events rederive` writes in one transaction. */
    rederiveBatch: EventsConfig.RederiveBatch,
    gvp: EventsGvpConfig,
    noaa: EventsNoaaConfig,
    wikidata: EventsWikidataConfig,
    links: EventsLinksConfig,
    leads: EventsLeadsConfig,
    siteTypes: SiteTypesConfig
) derives ConfigReader

object EventsConfig:
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends RefinedType[Int, Pure]
  type MinYear = MinYear.T
  object MinYear extends RefinedType[Long, Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends RefinedType[Long, Pure]
  type Region = Region.T
  object Region extends RefinedType[String, Pure]
  type MergeKm = MergeKm.T
  object MergeKm extends RefinedType[Double, Pure]
  type MergeYears = MergeYears.T
  object MergeYears extends RefinedType[Long, Pure]
  type DefaultLimit = DefaultLimit.T
  object DefaultLimit extends RefinedType[Int, Pure]
  type RederiveBatch = RederiveBatch.T
  object RederiveBatch extends RefinedType[Int, Pure]
