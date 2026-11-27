package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}

/** Events from catalogues (#1449): `events` in reference.conf. The HTTP politeness (User-Agent, retries, Retry-After
  * cap) is `media`'s; each catalogue has its own pace.
  */
@movesTo(FeaturePlace.CatalogueEvents)
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
  object TimeoutSeconds extends Newtype[Int, Pure]
  type MinYear = MinYear.T
  object MinYear extends Newtype[Long, Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends Newtype[Long, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type MergeKm = MergeKm.T
  object MergeKm extends Newtype[Double, Pure]
  type MergeYears = MergeYears.T
  object MergeYears extends Newtype[Long, Pure]
  type DefaultLimit = DefaultLimit.T
  object DefaultLimit extends Newtype[Int, Pure]
  type RederiveBatch = RederiveBatch.T
  object RederiveBatch extends Newtype[Int, Pure]
