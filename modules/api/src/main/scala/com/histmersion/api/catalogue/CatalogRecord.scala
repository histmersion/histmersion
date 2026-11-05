package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given

final case class CatalogRecord(
    provider: CatalogRecord.Provider,
    providerId: CatalogRecord.ProviderId,
    url: CatalogRecord.Url,
    label: CatalogRecord.Label,
    kind: CatalogRecord.Kind,
    timeSpan: TimeSpan,
    lat: Option[com.histmersion.api.catalogue.Latitude],
    lon: Option[com.histmersion.api.catalogue.Longitude],
    historicity: CatalogRecord.Historicity = Historicity.Attested,
    vei: Option[CatalogRecord.Vei] = None,
    magnitude: Option[CatalogRecord.Magnitude] = None,
    deaths: Option[CatalogRecord.Deaths] = None,
    wikidata: Option[CatalogRecord.Wikidata] = None,
    names: List[CatalogRecord.Names] = Nil,
    description: Option[CatalogRecord.Description] = None,
    group: Option[CatalogRecord.Group] = None,
    /** GVP's `StartDateYear`, NOAA's `year`: before any ± widened it. */
    central: Option[CatalogRecord.Central] = None,
    /** GVP's `StartDateYearUncertainty`. */
    plusMinus: Option[CatalogRecord.PlusMinus] = None,
    /** GVP's `StartEvidenceMethod`: `Tephrochronology`, `Isotopic: K/Ar`… */
    datingMethod: Option[CatalogRecord.DatingMethod] = None,
    raw: Option[CatalogRecord.Raw] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def key: (String, String) = (provider, providerId)
object CatalogRecord:


  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderId = ProviderId.T
  object ProviderId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Url = Url.T
  object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = Vei.T
  object Vei extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Magnitude = Magnitude.T
  object Magnitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deaths = Deaths.T
  object Deaths extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Group = Group.T
  object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def eventId(r: CatalogRecord): String = eventId(r.provider, r.providerId)

  def eventId(provider: String, providerId: String): String =
    (provider + "_" + providerId).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").stripSuffix("_")

  def owner(eventId: String, catalogue: String, keys: Iterable[(String, String)]): Option[(String, String)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2)
    own.find(k => CatalogRecord.eventId(k._1, k._2) == eventId).orElse(own.headOption)
