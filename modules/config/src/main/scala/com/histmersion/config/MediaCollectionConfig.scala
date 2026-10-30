package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** One open-access collection (`media.met`, `.cleveland`, `.aic`, `.loc`, `.pleiades`, `.open-context`, `.smithsonian`,
  * `.europeana`): its API, its own pace, how many records are looked at and kept per place, and the licence families it
  * may be stored under. `image-url` is the IIIF server where a collection has one; `max-year` the public-domain
  * boundary for maps, and for the museum collections that set it (Smithsonian, Europeana) the last year an object may
  * be made in (0 = no limit).
  */
final case class MediaCollectionConfig(
    enabled: Boolean,
    apiUrl: String,
    imageUrl: String,
    candidates: Int,
    perPlace: Int,
    requestIntervalMs: Int,
    displayPx: Int,
    maxYear: Int,
    licences: String,
    /** Seconds to wait, one step per 403 of a request, before asking again (#2561); "" = a 403 is final. */
    refusalBackoffSeconds: String = "",
    /** Comma-separated providers whose records are refused: Europeana's aggregators (`PROVIDER`, exact names), the
      * Smithsonian's units (`unit_code`).
      */
    excludeProviders: String = "",
    /** Comma-separated fragments of Europeana institution names (`DATA_PROVIDER`) whose records are refused. */
    excludeDataProviders: String = "",
    /** Comma-separated Europeana `dc:type` values whose records are refused. */
    excludeTypes: String = ""
) derives ConfigReader:
  /** [[refusalBackoffSeconds]] as milliseconds; an entry that is not a positive number is dropped. */
  def refusalBackoffMs: List[Long] =
    Option(refusalBackoffSeconds).toList
      .flatMap(_.split(","))
      .map(_.trim)
      .flatMap(_.toLongOption)
      .filter(_ > 0)
      .map(_ * 1000)
