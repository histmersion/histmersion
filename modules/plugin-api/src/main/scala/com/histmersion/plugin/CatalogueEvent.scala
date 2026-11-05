package com.histmersion.plugin

/** One event as a catalogue describes it. Its dating is a four-point span (years, inclusive): the start lies in
  * `[notBefore, beginBy]`, the end in `[endFrom, notAfter]`; an uncertainty widens the outer bounds and never invents a
  * certain core (leave `beginBy`/`endFrom` empty then).
  *
  * A catalogue that states only a year and its ± may leave the four points empty and give `central` and `plusMinus`:
  * the host then dates the event `central ± plusMinus`, as it dates its own catalogues' stated years. Either way the
  * host holds the dating inside the present and the catalogue's [[EventCatalogue.floorYear]] — at the import and again,
  * identically, at `events rederive`.
  *
  * @param id
  *   the catalogue's own key for it — stable, so a re-import finds it
  * @param url
  *   its page in the catalogue
  * @param kind
  *   `eruption`, `earthquake`, `battle`, `siege`, `treaty` … (an open vocabulary; the host normalises the spelling)
  * @param historicity
  *   `attested` | `inferred` | `contested` | `legendary` | `mythic`
  * @param vei
  *   an eruption's Volcanic Explosivity Index, as the catalogue gives it
  * @param magnitude
  *   an earthquake's magnitude
  * @param deaths
  *   deaths, as the catalogue gives them
  * @param wikidata
  *   the Wikidata item (`Q131386`) the catalogue names for it — merges it with the same event from another catalogue
  * @param names
  *   other names it goes by
  * @param group
  *   a key shared by records of the same thing across catalogues, where they say so
  * @param central
  *   the year the catalogue STATES, before any ± widened it
  * @param plusMinus
  *   the catalogue's stated ± in years around `central`
  * @param datingMethod
  *   how the catalogue dated it
  * @param raw
  *   the catalogue's own record as read (JSON text), kept with the event
  */
final case class CatalogueEvent(
    id: CatalogueEvent.Id,
    url: CatalogueEvent.Url,
    label: CatalogueEvent.Label,
    kind: CatalogueEvent.Kind,
    notBefore: Option[Year],
    notAfter: Option[Year],
    beginBy: Option[Year] = None,
    endFrom: Option[Year] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    historicity: CatalogueEvent.Historicity = "attested",
    vei: Option[CatalogueEvent.Vei] = None,
    magnitude: Option[CatalogueEvent.Magnitude] = None,
    deaths: Option[CatalogueEvent.Deaths] = None,
    wikidata: Option[WikidataId] = None,
    names: List[CatalogueEvent.Names] = Nil,
    description: Option[CatalogueEvent.Description] = None,
    group: Option[CatalogueEvent.Group] = None,
    central: Option[Year] = None,
    plusMinus: Option[CatalogueEvent.PlusMinus] = None,
    datingMethod: Option[CatalogueEvent.DatingMethod] = None,
    raw: Option[CatalogueEvent.Raw] = None
)

object CatalogueEvent:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Group = Group.T
  object Group extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlusMinus = PlusMinus.T
  object PlusMinus extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatingMethod = DatingMethod.T
  object DatingMethod extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
