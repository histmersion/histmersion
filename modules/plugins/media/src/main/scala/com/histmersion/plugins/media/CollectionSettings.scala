package com.histmersion
package plugins.media

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

import scala.annotation.targetName
import com.histmersion.plugin.MediaCollection
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
    refusalBackoffSeconds: CollectionSettings.RefusalBackoffSeconds = CollectionSettings.RefusalBackoffSeconds(""),
    excludeProviders: CollectionSettings.ExcludeProviders = CollectionSettings.ExcludeProviders(""),
    excludeDataProviders: CollectionSettings.ExcludeDataProviders = CollectionSettings.ExcludeDataProviders(""),
    excludeTypes: CollectionSettings.ExcludeTypes = CollectionSettings.ExcludeTypes("")
) derives ConfigReader:

  /** `refusal-backoff-seconds` as milliseconds, the blank and non-positive steps dropped. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refusalBackoffMs: List[MediaCollection.RefusalBackoffMs] =
    CollectionSettings
      .items(refusalBackoffSeconds)
      .flatMap(_.value.toLongOption)
      .filter(_ > 0)
      .map(s => MediaCollection.RefusalBackoffMs(s * 1000))

  /** `max-year` as the last year an object may be made in; 0 sets none. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def latestYear: Option[CollectionSettings.MaxYear] = Option.when(maxYear.value != 0)(maxYear)

object CollectionSettings:
  type Item = Item.T
  object Item extends Newtype[String, Pure]

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
  type RefusalBackoffSeconds = RefusalBackoffSeconds.T
  object RefusalBackoffSeconds extends Newtype[String, Pure]
  type ExcludeProviders = ExcludeProviders.T
  object ExcludeProviders extends Newtype[String, Pure]
  type ExcludeDataProviders = ExcludeDataProviders.T
  object ExcludeDataProviders extends Newtype[String, Pure]
  type ExcludeTypes = ExcludeTypes.T
  object ExcludeTypes extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def split(setting: String): List[Item] = setting.split(",").toList.map(_.trim).filter(_.nonEmpty).map(Item(_))

  /** Pure: the entries of a comma-separated setting, trimmed, blanks dropped. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @targetName("itemsOfExcludeProviders")
  def items(list: ExcludeProviders): List[Item] = Option(list).toList.flatMap(l => split(l.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @targetName("itemsOfExcludeDataProviders")
  def items(list: ExcludeDataProviders): List[Item] = Option(list).toList.flatMap(l => split(l.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @targetName("itemsOfExcludeTypes")
  def items(list: ExcludeTypes): List[Item] = Option(list).toList.flatMap(l => split(l.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @targetName("itemsOfRefusalBackoffSeconds")
  def items(list: RefusalBackoffSeconds): List[Item] = Option(list).toList.flatMap(l => split(l.value))
