package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Historicity

// ── one record of a catalogue ───────────────────────────────────────────────────────────────────

/** What one catalogue says about one event. `providerId` is the catalogue's own key (a GVP eruption number,
  * `volcano:1351` at NOAA, a Wikidata item); `group` is a key shared by records of the same thing across catalogues
  * where the catalogues say so (the GVP volcano number NOAA quotes for an eruption). `magnitude` (an earthquake's),
  * `vei` (an eruption's Volcanic Explosivity Index) and `deaths` are as the catalogue gives them.
  */
final case class CatalogRecord(
    provider: String,
    providerId: String,
    url: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    lat: Option[Double],
    lon: Option[Double],
    historicity: String = Historicity.Attested,
    vei: Option[Int] = None,
    magnitude: Option[Double] = None,
    deaths: Option[Long] = None,
    wikidata: Option[String] = None,
    names: List[String] = Nil,
    description: Option[String] = None,
    group: Option[String] = None,
    /** The year the catalogue STATES (GVP's `StartDateYear`, NOAA's `year`), before any ± widened it. */
    central: Option[Long] = None,
    /** The catalogue's stated ± in years around [[central]] (GVP's `StartDateYearUncertainty`). */
    plusMinus: Option[Long] = None,
    /** How the catalogue dated it (GVP's `StartEvidenceMethod`: `Tephrochronology`, `Isotopic: K/Ar`…). */
    datingMethod: Option[String] = None,
    /** The catalogue's own record as read (JSON text: a GVP feature, a NOAA item, the Wikidata rows of the item) — what
      * `events rederive` parses again with no network.
      */
    raw: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The key it is stored under in `event_catalogue`. */
  def key: (String, String) = (provider, providerId)
object CatalogRecord:
  given Schema[CatalogRecord] = summon[KindlingsSchema[CatalogRecord]].schema

  /** Pure: the id an event created from this record gets (`gvp_13879`, `noaa_volcano_1351`, `wikidata_q131386`).
    * Stable, so a re-import finds it.
    */
  def eventId(r: CatalogRecord): String = eventId(r.provider, r.providerId)

  def eventId(provider: String, providerId: String): String =
    (provider + "_" + providerId).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").stripSuffix("_")

  /** Pure: which of an event's catalogue records it is refreshed from (its OWNER): among the records of the catalogue
    * that created it, the one whose id it carries, else the lowest id.
    */
  def owner(eventId: String, catalogue: String, keys: Iterable[(String, String)]): Option[(String, String)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2)
    own.find(k => CatalogRecord.eventId(k._1, k._2) == eventId).orElse(own.headOption)
