package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** One collection's `histmersion.media.<collection>` block: its switch, its API, how many records it reads and keeps
  * per place, its pace and its patience with a refusal, the rendition width, the last year an object or map may be made
  * in, and (the keyed ones) the units, institutions and types it leaves out.
  */
final case class CollectionSettings(
    enabled: CollectionSettings.Enabled,
    apiUrl: CollectionSettings.ApiUrl,
    imageUrl: CollectionSettings.ImageUrl,
    candidates: CollectionSettings.Candidates,
    perPlace: CollectionSettings.PerPlace,
    requestIntervalMs: CollectionSettings.RequestIntervalMs,
    displayPx: CollectionSettings.DisplayPx,
    maxYear: CollectionSettings.MaxYear,
    refusalBackoffSeconds: CollectionSettings.RefusalBackoffSeconds = com.histmersion.plugins.media.CollectionSettings.RefusalBackoffSeconds(""),
    excludeProviders: CollectionSettings.ExcludeProviders = com.histmersion.plugins.media.CollectionSettings.ExcludeProviders(""),
    excludeDataProviders: CollectionSettings.ExcludeDataProviders = com.histmersion.plugins.media.CollectionSettings.ExcludeDataProviders(""),
    excludeTypes: CollectionSettings.ExcludeTypes = com.histmersion.plugins.media.CollectionSettings.ExcludeTypes("")
) derives ConfigReader:

  /** `refusal-backoff-seconds` as milliseconds, the blank and non-positive steps dropped. */
  def refusalBackoffMs: List[com.histmersion.plugins.media.CollectionSettings.RefusalBackoffMsResult] =
    CollectionSettings.items(refusalBackoffSeconds).flatMap(_.toLongOption).filter(_ > 0).map(_ * 1000)

  /** `max-year` as the last year an object may be made in; 0 sets none. */
  def latestYear: Option[com.histmersion.plugins.media.CollectionSettings.MaxYear] = Option.when(maxYear != 0)(maxYear)

object CollectionSettings:
  type Item = Item.T
  object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type List = List.T
  object List extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Pure: the entries of a comma-separated setting, trimmed, blanks dropped. */
  type RefusalBackoffMsResult = RefusalBackoffMsResult.T
  object RefusalBackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  type RefusalBackoffSeconds = RefusalBackoffSeconds.T
  object RefusalBackoffSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeProviders = ExcludeProviders.T
  object ExcludeProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeDataProviders = ExcludeDataProviders.T
  object ExcludeDataProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludeTypes = ExcludeTypes.T
  object ExcludeTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def items(list: com.histmersion.plugins.media.CollectionSettings.List): List[com.histmersion.plugins.media.CollectionSettings.Item] = Option(list).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
