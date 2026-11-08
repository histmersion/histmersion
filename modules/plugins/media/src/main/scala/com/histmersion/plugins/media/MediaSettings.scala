package com.histmersion
package plugins.media

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion.media` as the collections read it: the pace, the widths of the stored rendition and of the card's
  * thumbnail, the pictures kept per Wikidata statement kind, Wikidata's and Commons' APIs, the depictions' limits, and
  * each collection's block.
  */
final case class MediaSettings(
    requestIntervalMs: MediaSettings.RequestIntervalMs,
    displayPx: MediaSettings.DisplayPx,
    thumbPx: MediaSettings.ThumbPx,
    perProperty: MediaSettings.PerProperty,
    wikidata: WikidataSettings,
    commons: Commons.Settings,
    depicts: DepictsSettings,
    met: CollectionSettings,
    cleveland: CollectionSettings,
    aic: CollectionSettings,
    loc: CollectionSettings,
    pleiades: CollectionSettings,
    openContext: CollectionSettings,
    smithsonian: CollectionSettings,
    europeana: CollectionSettings
) derives ConfigReader

object MediaSettings:
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends Newtype[Int, Pure]
  type ThumbPx = ThumbPx.T
  object ThumbPx extends Newtype[Int, Pure]
  type PerProperty = PerProperty.T
  object PerProperty extends Newtype[Int, Pure]
