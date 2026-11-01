package com.histmersion.domain.media.imports

/** A place as a collection match sees it: every name key it goes by (label and alternative names,
  * `histmersion_place_key`), its period — its own when bounded at both ends, else the span of the findings it holds —
  * and its Wikidata item and point (to tell equivalents apart).
  */
final case class PlaceFacts(
    id: String,
    label: String,
    keys: Set[String],
    period: Option[(Long, Long)],
    qid: Option[String],
    lat: Option[Double],
    lon: Option[Double]
)
