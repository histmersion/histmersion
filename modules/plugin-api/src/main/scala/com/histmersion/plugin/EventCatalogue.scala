package com.histmersion.plugin

/** Event catalogues, the third part of the app that plugins extend: a catalogue of events — eruptions, earthquakes,
  * battles, treaties — that `histmersion events import <id>` reads into the map as RESEARCH LEADS (drawn lighter than
  * the events a source in the corpus attests). A plugin adds one or more [[EventCatalogue]]s to
  * [[PluginContext.eventCatalogues]].
  *
  * The plugin READS: it splits one import into parts and returns each part's records. The host owns the rest: which
  * parts are read (a part read before is not read again unless `--refresh`), the years and the `--bbox`/size
  * thresholds, holding a dating inside the present, merging a record with the event another catalogue (or an earlier
  * import) already holds, storing it, and the card's provenance line (the catalogue's title, licence and citation).
  */

/** What one import asks for.
  *
  * @param from
  *   the earliest year asked for, when the import is bounded
  * @param to
  *   the latest year asked for
  * @param area
  *   `(minLon, minLat, maxLon, maxLat)`, when the import is limited to an area — a filter the host applies anyway; a
  *   catalogue whose API can narrow by area may use it
  */
final case class CatalogueQuery(
    from: Option[Year],
    to: Year,
    area: Option[(Longitude, Latitude, Longitude, Latitude)] = None
)

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

/** What one part of an import answered: its records, and the part to read after it when the catalogue pages its answers
  * (`next`), which the host reads in turn.
  */
final case class CataloguePage(events: List[CatalogueEvent], next: Option[String] = None)

/** One catalogue. Called by the host from one import at a time; failures are values (`Left`), an exception is caught by
  * the host and reported for that part.
  */
trait EventCatalogue:
  /** What to call it: "Smithsonian Global Volcanism Program". */
  def title: String

  /** The terms its data comes under, as the provider states them. */
  def licence: Licence

  /** How it asks to be cited. */
  def citation: String

  /** Its home page. */
  def home: String

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: Int = 1000

  /** The earliest year any of its events can be, when the catalogue covers a period that starts there — as the
    * Smithsonian's list is of HOLOCENE eruptions (`events.gvp.floor-year`). A dating whose uncertainty reaches further
    * back is held at it by the host. `None` (the default): no floor.
    */
  def floorYear: Option[Year] = None

  /** The parts of one import, in order (time windows, classes, datasets) — each a stable key, so a part read and
    * recorded is not asked again by the next import.
    */
  def parts(query: CatalogueQuery): List[String]

  /** The records of one part. `http` is the host's polite client of this import. */
  def read(part: String, query: CatalogueQuery, http: PluginHttp): Either[String, CataloguePage]
