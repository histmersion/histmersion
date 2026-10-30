package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One event on the map (`GET /api/events/geo`, the playback layer): where to draw it and how. `lat`/`lon` is its
  * ANCHOR — its own point, else the point-on-surface of its own area, else its place's point or the point-on-surface of
  * the place's area. `outline` is the GeoJSON geometry of the area a REGIONAL event covers (simplified for drawing),
  * absent for a local event and for an area known only by a point. `anchor` says where the position came from: `event` |
  * `place`.
  */
final case class EventGeo(
    id: String,
    label: String,
    kind: String,
    scale: String,
    historicity: String,
    timeSpan: TimeSpan,
    lat: Double,
    lon: Double,
    anchor: String,
    placeId: Option[String] = None,
    placeLabel: Option[String] = None,
    parentId: Option[String] = None,
    outline: Option[String] = None,
    /** [[EventTier]]: `attested` (full-strength marker) or `catalogue` (outlined: a research lead). */
    tier: String = EventTier.Attested,
    /** The catalogue's size of it, for the marker's title (`VEI 7`, `M 7.3`). */
    size: Option[String] = None,
    /** [[EventPrecision]]: `approximate` is drawn faded; an `imprecise` one never reaches the map's feed. */
    precision: String = EventPrecision.Precise,
    /** The stated dating in words (`250 CE ± 2500 (tephrochronology)`), for the marker's title. */
    dating: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventGeo:
  given Schema[EventGeo] = summon[KindlingsSchema[EventGeo]].schema

  /** Pure: the years an event covers in every time view — its outer span (`notBefore` else `beginBy`, to `notAfter`
    * else `endFrom`). An OPEN-ENDED event (one end known, #1768) covers the end it states alone — an event with only a
    * start is an event at its start (its start window when `notBefore` and `beginBy` are both known), as the domain's
    * `CatalogDating.span` writes a catalogue's — never every year after it: an event still going on states an end (the
    * present year); nothing is inferred from its kind. An undated one, nothing. The database's generated
    * `events.outer_span` (V089) is the same rule (`EventOuterSpanSuite` checks the two agree), so the map's feed and
    * the web draw the same years.
    */
  def outer(ts: TimeSpan): Option[(Long, Long)] =
    val start = ts.notBefore.orElse(ts.beginBy).orElse(ts.endFrom).orElse(ts.notAfter)
    val end = ts.notAfter.orElse(ts.endFrom).orElse(ts.beginBy).orElse(ts.notBefore)
    for a <- start; b <- end yield (a.min(b), a.max(b))

  /** Pure: what a card says of an open-ended event's missing side ([[outer]] drew it at the side it states):
    * `end not recorded`, `start not recorded`, or nothing (both known, or undated).
    */
  def missingEnd(ts: TimeSpan): Option[String] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some("end not recorded")
    else if hasEnd && !hasStart then Some("start not recorded")
    else None
