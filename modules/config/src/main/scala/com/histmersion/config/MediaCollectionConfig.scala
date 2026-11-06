package com.histmersion.config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  def refusalBackoffMs: List[com.histmersion.config.MediaCollectionConfig.RefusalBackoffMs] =
    Option(refusalBackoffSeconds).toList
      .flatMap(_.value.split(","))
      .map(_.trim)
      .flatMap(_.toLongOption)
      .filter(_ > 0)
      .map(ms => MediaCollectionConfig.RefusalBackoffMs(ms * 1000))

object MediaCollectionConfig:
  type RefusalBackoffMs = RefusalBackoffMs.T
  object RefusalBackoffMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Enabled = Enabled.T
  object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiUrl = ApiUrl.T
  object ApiUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImageUrl = ImageUrl.T
  object ImageUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Candidates = Candidates.T
  object Candidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerPlace = PerPlace.T
  object PerPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Licences = Licences.T
  object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalBackoffSeconds = RefusalBackoffSeconds.T
  object RefusalBackoffSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeProviders = ExcludeProviders.T
  object ExcludeProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeDataProviders = ExcludeDataProviders.T
  object ExcludeDataProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeTypes = ExcludeTypes.T
  object ExcludeTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
