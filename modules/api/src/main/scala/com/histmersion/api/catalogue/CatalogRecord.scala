package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.values.Historicity
import com.histmersion.api.TimeSpan
import com.histmersion.api.given

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
    /** GVP's `StartDateYear`, NOAA's `year`: before any ± widened it. */
    central: Option[Long] = None,
    /** GVP's `StartDateYearUncertainty`. */
    plusMinus: Option[Long] = None,
    /** GVP's `StartEvidenceMethod`: `Tephrochronology`, `Isotopic: K/Ar`… */
    datingMethod: Option[String] = None,
    raw: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def key: (String, String) = (provider, providerId)
object CatalogRecord:
  given Schema[CatalogRecord] = summon[KindlingsSchema[CatalogRecord]].schema

  def eventId(r: CatalogRecord): String = eventId(r.provider, r.providerId)

  def eventId(provider: String, providerId: String): String =
    (provider + "_" + providerId).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").stripSuffix("_")

  def owner(eventId: String, catalogue: String, keys: Iterable[(String, String)]): Option[(String, String)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2)
    own.find(k => CatalogRecord.eventId(k._1, k._2) == eventId).orElse(own.headOption)
