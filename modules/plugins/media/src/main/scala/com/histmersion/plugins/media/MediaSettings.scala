package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.media` as the collections read it: the pace, the widths of the stored rendition and of the card's
  * thumbnail, the pictures kept per Wikidata statement kind, Wikidata's and Commons' APIs, the depictions' limits, and
  * each collection's block.
  */
final case class MediaSettings(
    requestIntervalMs: Int,
    displayPx: Int,
    thumbPx: Int,
    perProperty: Int,
    wikidata: WikidataSettings,
    commons: CommonsSettings,
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
