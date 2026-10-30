package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── search results: the SOURCE is the unit of every result (plan §1.0) ─────────────────────────

/** One finding of a hit source that satisfied the filter, with the criteria it satisfied. `why` entries are e.g.
  * `period`, `bbox`, `place: Anatolia`, `tag: sea-peoples`.
  */
final case class FindingMatch(
    id: String,
    claim: String,
    regionName: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    periodName: Option[String],
    tags: List[String],
    why: List[String],
    /** The gazetteer places this finding was materialised into (plan §1.6a); empty until `histmersion places rebuild`
      * has run.
      */
    places: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object FindingMatch:
  given Schema[FindingMatch] = summon[KindlingsSchema[FindingMatch]].schema
