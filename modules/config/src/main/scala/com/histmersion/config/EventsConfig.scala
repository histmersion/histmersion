package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinYear = MinYear.T
  object MinYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Region = Region.T
  object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeKm = MergeKm.T
  object MergeKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeYears = MergeYears.T
  object MergeYears extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefaultLimit = DefaultLimit.T
  object DefaultLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RederiveBatch = RederiveBatch.T
  object RederiveBatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
