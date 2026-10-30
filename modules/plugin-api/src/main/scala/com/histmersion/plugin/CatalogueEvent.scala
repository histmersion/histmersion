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
    id: String,
    url: String,
    label: String,
    kind: String,
    notBefore: Option[Year],
    notAfter: Option[Year],
    beginBy: Option[Year] = None,
    endFrom: Option[Year] = None,
    lat: Option[Latitude] = None,
    lon: Option[Longitude] = None,
    historicity: String = "attested",
    vei: Option[Int] = None,
    magnitude: Option[Double] = None,
    deaths: Option[Long] = None,
    wikidata: Option[WikidataId] = None,
    names: List[String] = Nil,
    description: Option[String] = None,
    group: Option[String] = None,
    central: Option[Year] = None,
    plusMinus: Option[Long] = None,
    datingMethod: Option[String] = None,
    raw: Option[String] = None
)
