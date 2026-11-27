package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.TimeSpan
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
final case class CatalogRecord(
    provider: CatalogRecord.Provider,
    providerId: CatalogRecord.ProviderId,
    url: CatalogRecord.Url,
    label: CatalogRecord.Label,
    kind: CatalogRecord.Kind,
    timeSpan: TimeSpan,
    lat: Option[Latitude],
    lon: Option[Longitude],
    historicity: CatalogRecord.Historicity =
      CatalogRecord.Historicity(com.histmersion.api.catalogue.Historicity.Attested),
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
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key: (CatalogRecord.Provider, EventCatalogueRef.ProviderId) =
    (CatalogRecord.Provider((provider).value), EventCatalogueRef.ProviderId((providerId).value))
object CatalogRecord:
  object Wikidata:
    type Class = Class.T
    object Class extends Newtype[String, Pure]
    type ImportClass = ImportClass.T
    object ImportClass extends Newtype[String, Pure]
    type P31Json = P31Json.T
    object P31Json extends Newtype[String, Pure]
  type EventId = EventId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object EventId extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type EventIdProvider = Provider
  val EventIdProvider: Provider.type = Provider
  export EventCatalogueRef.ProviderId as EventIdProviderId

  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  export EventCatalogueRef.{ProviderId, Url}
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Historicity = Historicity.T
  object Historicity extends Newtype[String, Pure]
  export EventCatalogueRef.{Deaths, Magnitude, Vei}
  type Wikidata = EventEntry.Wikidata
  export EventEntry.Names
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Group = Group.T
  object Group extends Newtype[String, Pure]:
    type Label = Label.T
    object Label extends Newtype[String, Pure]
  type Central = Central.T
  object Central extends Newtype[Long, Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends Newtype[Long, Pure]
  export EventDating.Method as DatingMethod
  type Raw = Raw.T
  object Raw extends Newtype[String, Pure]

  def eventId(r: CatalogRecord): EventId = eventId(r.provider, r.providerId)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def eventId(provider: EventIdProvider, providerId: CatalogRecord.EventIdProviderId): EventId =
    EventId(
      ((provider).value + "_" + providerId)
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "_")
        .stripSuffix("_")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def owner(
      eventId: EventId,
      catalogue: Provider,
      keys: Iterable[(Provider, EventCatalogueRef.ProviderId)]
  ): Option[(Provider, EventCatalogueRef.ProviderId)] =
    val own = keys.filter(_._1 == catalogue).toList.sortBy(_._2.value)
    (own
      .find(k => CatalogRecord.eventId(Provider((k._1).value), EventCatalogueRef.ProviderId((k._2).value)) == eventId)
      .orElse(own.headOption))
      .map { refactorValue2 =>
        val refactorTuple2 = refactorValue2;
        (Provider((refactorTuple2._1).value), EventCatalogueRef.ProviderId((refactorTuple2._2).value))
      }
