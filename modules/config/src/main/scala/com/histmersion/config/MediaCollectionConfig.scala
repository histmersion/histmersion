package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** One open-access collection (`media.met`, `.cleveland`, `.aic`, `.loc`, `.pleiades`, `.open-context`, `.smithsonian`,
  * `.europeana`): its API, its own pace, how many records are looked at and kept per place, and the licence families it
  * may be stored under. `image-url` is the IIIF server where a collection has one; `max-year` the public-domain
  * boundary for maps, and for the museum collections that set it (Smithsonian, Europeana) the last year an object may
  * be made in (0 = no limit).
  */
final case class MediaCollectionConfig(
    enabled: MediaCollectionConfig.Enabled,
    apiUrl: MediaCollectionConfig.ApiUrl,
    imageUrl: MediaCollectionConfig.ImageUrl,
    candidates: MediaCollectionConfig.Candidates,
    perPlace: MediaCollectionConfig.PerPlace,
    requestIntervalMs: MediaCollectionConfig.RequestIntervalMs,
    displayPx: MediaCollectionConfig.DisplayPx,
    maxYear: MediaCollectionConfig.MaxYear,
    licences: MediaCollectionConfig.Licences,
    /** Seconds to wait, one step per 403 of a request, before asking again (#2561); "" = a 403 is final. */
    refusalBackoffSeconds: MediaCollectionConfig.RefusalBackoffSeconds = MediaCollectionConfig.RefusalBackoffSeconds(""),
    /** Comma-separated providers whose records are refused: Europeana's aggregators (`PROVIDER`, exact names), the
      * Smithsonian's units (`unit_code`).
      */
    excludeProviders: MediaCollectionConfig.ExcludeProviders = MediaCollectionConfig.ExcludeProviders(""),
    /** Comma-separated fragments of Europeana institution names (`DATA_PROVIDER`) whose records are refused. */
    excludeDataProviders: MediaCollectionConfig.ExcludeDataProviders = MediaCollectionConfig.ExcludeDataProviders(""),
    /** Comma-separated Europeana `dc:type` values whose records are refused. */
    excludeTypes: MediaCollectionConfig.ExcludeTypes = MediaCollectionConfig.ExcludeTypes("")
) derives ConfigReader:
  /** [[refusalBackoffSeconds]] as milliseconds; an entry that is not a positive number is dropped. */
  def refusalBackoffMs: List[MediaCollectionConfig.RefusalBackoffMs] =
    Option(refusalBackoffSeconds).toList
      .flatMap(_.value.split(","))
      .map(_.trim)
      .flatMap(_.toLongOption)
      .filter(_ > 0)
      .map(ms => MediaCollectionConfig.RefusalBackoffMs(ms * 1000))

object MediaCollectionConfig:
  type RefusalBackoffMs = RefusalBackoffMs.T
  object RefusalBackoffMs extends Newtype[Long, Pure]

  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  type ApiUrl = ApiUrl.T
  object ApiUrl extends Newtype[String, Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends Newtype[String, Pure]
  type Candidates = Candidates.T
  object Candidates extends Newtype[Int, Pure]
  type PerPlace = PerPlace.T
  object PerPlace extends Newtype[Int, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends Newtype[Int, Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends Newtype[Int, Pure]
  type Licences = Licences.T
  object Licences extends Newtype[String, Pure]
  type RefusalBackoffSeconds = RefusalBackoffSeconds.T
  object RefusalBackoffSeconds extends Newtype[String, Pure]
  type ExcludeProviders = ExcludeProviders.T
  object ExcludeProviders extends Newtype[String, Pure]
  type ExcludeDataProviders = ExcludeDataProviders.T
  object ExcludeDataProviders extends Newtype[String, Pure]
  type ExcludeTypes = ExcludeTypes.T
  object ExcludeTypes extends Newtype[String, Pure]
