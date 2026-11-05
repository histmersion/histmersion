package com.histmersion.plugins.media

import hearth.kindlings.sconfigderivation.ConfigReader

/** One collection's `histmersion.media.<collection>` block: its switch, its API, how many records it reads and keeps
  * per place, its pace and its patience with a refusal, the rendition width, the last year an object or map may be made
  * in, and (the keyed ones) the units, institutions and types it leaves out.
  */
final case class CollectionSettings(
    enabled: Boolean,
    apiUrl: String,
    imageUrl: String,
    candidates: Int,
    perPlace: Int,
    requestIntervalMs: Int,
    displayPx: Int,
    maxYear: Int,
    refusalBackoffSeconds: String = "",
    excludeProviders: String = "",
    excludeDataProviders: String = "",
    excludeTypes: String = ""
) derives ConfigReader:

  /** `refusal-backoff-seconds` as milliseconds, the blank and non-positive steps dropped. */
  def refusalBackoffMs: List[Long] =
    CollectionSettings.items(refusalBackoffSeconds).flatMap(_.toLongOption).filter(_ > 0).map(_ * 1000)

  /** `max-year` as the last year an object may be made in; 0 sets none. */
  def latestYear: Option[Int] = Option.when(maxYear != 0)(maxYear)

object CollectionSettings:
  /** Pure: the entries of a comma-separated setting, trimmed, blanks dropped. */
  def items(list: String): List[String] = Option(list).toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)
