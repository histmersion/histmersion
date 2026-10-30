package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── wire DTOs ───────────────────────────────────────────────────────────────────────────────────

/** A gazetteer row as the API returns it. `names` is the comma-joined alternates (the store's arrays are flattened on
  * the wire exactly as `SearchHit` flattens `topics`).
  */
final case class PlaceSummary(
    id: String,
    label: String,
    kind: String,
    periodStart: Option[Long],
    periodEnd: Option[Long],
    source: Option[String],
    names: String,
    /** `properties.contested` — the shape is disputed (overlapping claims, a debated existence). Surfaced everywhere a
      * label is drawn, so nobody mistakes a hand-traced guess for a fact.
      */
    contested: Boolean = false,
    /** How much of `periodStart`/`periodEnd` the place's source states ([[PlaceDating]]). The two bounds are always the
      * STATED years: an unknown start is `None` here, never the window the time filters use.
      */
    dating: PlaceDating = PlaceDating.Stated,
    /** Who brought the place in: `wikidata`, `geonames`, `lpf:<dataset>`; `None` = the bundled seed or a person's own
      * place.
      */
    importSource: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** "end 1769 CE, start unknown", "date unknown", "1300 BCE–1100 BCE" (see [[PlaceDating.describe]]). */
  def when: String = PlaceDating.describe(dating, periodStart, periodEnd)
object PlaceSummary:
  given Schema[PlaceSummary] = summon[KindlingsSchema[PlaceSummary]].schema
