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
  def key: (com.histmersion.api.catalogue.CatalogRecord.Provider, com.histmersion.api.catalogue.EventCatalogueRef.ProviderId) = (_root_.com.histmersion.api.catalogue.CatalogRecord.Provider((provider).value), _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId((providerId).value))
object CatalogRecord:
  object Wikidata:
    type Class = Class.T
    object Class extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportClass = ImportClass.T
    object ImportClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type P31Json = P31Json.T
    object P31Json extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Group:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EventId = EventId.T
  object EventId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type EventIdProvider = _root_.com.histmersion.api.catalogue.CatalogRecord.Provider
  val EventIdProvider: _root_.com.histmersion.api.catalogue.CatalogRecord.Provider.type = _root_.com.histmersion.api.catalogue.CatalogRecord.Provider
  type EventIdProviderId = _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId
  val EventIdProviderId: _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId
  type OwnerKeysPart2 = OwnerKeysPart2.T
  object OwnerKeysPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)

  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProviderId = com.histmersion.api.catalogue.EventCatalogueRef.ProviderId
  val ProviderId: _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId
  type Url = com.histmersion.api.catalogue.EventCatalogueRef.Url
  val Url: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Url.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Url
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Historicity = Historicity.T
  object Historicity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Vei = com.histmersion.api.catalogue.EventCatalogueRef.Vei
  val Vei: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Vei.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Vei
  type Magnitude = com.histmersion.api.catalogue.EventCatalogueRef.Magnitude
  val Magnitude: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Magnitude.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Magnitude
  type Deaths = com.histmersion.api.catalogue.EventCatalogueRef.Deaths
  val Deaths: _root_.com.histmersion.api.catalogue.EventCatalogueRef.Deaths.type = _root_.com.histmersion.api.catalogue.EventCatalogueRef.Deaths
  type Wikidata = com.histmersion.api.catalogue.EventEntry.Wikidata
  val Wikidata: _root_.com.histmersion.api.catalogue.EventEntry.Wikidata.type = _root_.com.histmersion.api.catalogue.EventEntry.Wikidata
  type Names = com.histmersion.api.catalogue.EventEntry.Names
  val Names: _root_.com.histmersion.api.catalogue.EventEntry.Names.type = _root_.com.histmersion.api.catalogue.EventEntry.Names
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Group = Group.T
  object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Central = Central.T
  object Central extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = com.histmersion.api.catalogue.EventDating.Method
  val DatingMethod: _root_.com.histmersion.api.catalogue.EventDating.Method.type = _root_.com.histmersion.api.catalogue.EventDating.Method
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def eventId(r: CatalogRecord): com.histmersion.api.catalogue.CatalogRecord.EventId = eventId(r.provider, r.providerId)

  def eventId(provider: com.histmersion.api.catalogue.CatalogRecord.EventIdProvider, providerId: com.histmersion.api.catalogue.CatalogRecord.EventIdProviderId): com.histmersion.api.catalogue.CatalogRecord.EventId =
    _root_.com.histmersion.api.catalogue.CatalogRecord.EventId(((provider).value + "_" + providerId).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "_").stripSuffix("_"))

  def owner(eventId: com.histmersion.api.catalogue.CatalogRecord.EventId, catalogue: com.histmersion.api.catalogue.CatalogRecord.Provider, keys: Iterable[(com.histmersion.api.catalogue.CatalogRecord.Provider, com.histmersion.api.catalogue.EventCatalogueRef.ProviderId)]): Option[(com.histmersion.api.catalogue.CatalogRecord.Provider, com.histmersion.api.catalogue.EventCatalogueRef.ProviderId)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2)
    (own.find(k => CatalogRecord.eventId(_root_.com.histmersion.api.catalogue.CatalogRecord.Provider((k._1).value), _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId((k._2).value)) == eventId).orElse(own.headOption)).map(refactorValue2 => { val refactorTuple2 = refactorValue2; (_root_.com.histmersion.api.catalogue.CatalogRecord.Provider((refactorTuple2._1).value), _root_.com.histmersion.api.catalogue.EventCatalogueRef.ProviderId((refactorTuple2._2).value)) })
