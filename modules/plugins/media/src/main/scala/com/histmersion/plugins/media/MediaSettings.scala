package com.histmersion.plugins.media

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

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
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DisplayPx = DisplayPx.T
  object DisplayPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbPx = ThumbPx.T
  object ThumbPx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerProperty = PerProperty.T
  object PerProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
