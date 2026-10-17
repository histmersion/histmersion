package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** The EVENTS database (plan §1.2): CIDOC-CRM compatible events with four-point time-spans,
  * hierarchical composition, causal/temporal edges and participant roles.
  *
  * A four-point time-span records both the certain and the uncertain bounds of an event's
  * temporal extent: `notBefore` / `beginBy` / `endFrom` / `notAfter`. The outer span
  * (notBefore..notAfter) covers every possible moment; the inner span (beginBy..endFrom)
  * covers only the certain core. Either bound may be absent (unknown). */

// ── vocabulary ─────────────────────────────────────────────────────────────────────────────────

object EventKind:
  val Event        = "event"
  val Battle       = "battle"
  val Migration    = "migration"
  val Construction = "construction"
  val Destruction  = "destruction"
  val Founding     = "founding"
  val Treaty       = "treaty"
  val Reign        = "reign"
  val EventPeriod  = "period"
  val all: List[String] = List(Event, Battle, Migration, Construction, Destruction,
    Founding, Treaty, Reign, EventPeriod)

/** An event's SCALE (#1440): LOCAL — it happened at one place (a battle, a siege, a destruction); or
  * REGIONAL — it happened across a region (a migration, a technological change, an era, a war, whose
  * battles are its local sub-events). The map's playback draws a local event as a point and label at
  * its site, fading after it ends, and a regional one as a label at the region's centre with the
  * region's outline for its span.
  *
  * The stored value is optional; [[effective]] derives the scale of an event that states none. */
object EventScale:
  val Local    = "local"
  val Regional = "regional"
  /** On a patch: forget the stated scale, derive it again. Never stored. */
  val Auto     = "auto"
  val all: List[String] = List(Local, Regional)

  /** Kinds that happen at ONE place. */
  val localKinds: Set[String] = Set("battle", "naval_battle", "siege", "destruction", "construction", "founding",
    "eruption", "earthquake", "tsunami", "flood")

  /** Kinds that happen across a region (`*_era` too: `diplomatic_era`). */
  val regionalKinds: Set[String] = Set("migration", "technological_change", "cultural_shift", "era",
    "period", "reign", "war", "invasion", "collapse", "polity_formation", "drought", "climate_event", "epidemic",
    "famine")

  /** Pure: `local`/`regional` as stored, `auto` as `Some(None)` (clear), anything else `None`. */
  def parse(s: String): Option[Option[String]] =
    s.trim.toLowerCase match
      case Local    => Some(Some(Local))
      case Regional => Some(Some(Regional))
      case Auto     => Some(None)
      case _        => None

  /** Pure: the normalised kind (`Technological change` → `technological_change`). */
  def normKind(kind: String): String = kind.trim.toLowerCase.replaceAll("[\\s-]+", "_")

  /** Pure: the scale an event's KIND implies, if it implies one. */
  def ofKind(kind: String): Option[String] =
    val k = normKind(kind)
    if localKinds(k) then Some(Local)
    else if regionalKinds(k) || k.endsWith("_era") then Some(Regional)
    else None

  /** Pure: the scale a PLACE's kind implies — a site or feature is a place where something happens, a
    * region, polity or people an area. */
  def ofPlaceKind(placeKind: String): Option[String] =
    placeKind.trim.toLowerCase match
      case PlaceKind.Site | PlaceKind.Feature                       => Some(Local)
      case PlaceKind.Region | PlaceKind.Polity | PlaceKind.People   => Some(Regional)
      case _                                                         => None

  /** Pure: what an event's own geometry implies (a GeoJSON or PostGIS geometry type): a point is a
    * place, a polygon an area. */
  def ofGeometry(geometryType: String): Option[String] =
    geometryType.trim.toLowerCase.replace("st_", "") match
      case "point"                      => Some(Local)
      case "polygon" | "multipolygon"   => Some(Regional)
      case _                            => None

  /** Pure: the scale an event has. In order: the STATED one; its kind's (a war is regional, its
    * battles local); its own geometry's (what the map draws it at, when it has one); its place's
    * kind's; `local` for a sub-event that says nothing else (a part of a larger event); else `local`. */
  def effective(stated: Option[String], kind: String, hasParent: Boolean,
      placeKind: Option[String], geometryType: Option[String]): String =
    stated.flatMap(s => parse(s).flatten)
      .orElse(ofKind(kind))
      .orElse(geometryType.flatMap(ofGeometry))
      .orElse(placeKind.flatMap(ofPlaceKind))
      .orElse(Option.when(hasParent)(Local))
      .getOrElse(Local)

object Historicity:
  val Attested  = "attested"
  val Inferred  = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic    = "mythic"
  val all: List[String] = List(Attested, Inferred, Contested, Legendary, Mythic)

object EventEdgeKind:
  val Before     = "before"
  val Meets      = "meets"
  val Overlaps   = "overlaps"
  val During     = "during"
  val Starts     = "starts"
  val Finishes   = "finishes"
  val Equals     = "equals"
  val Causes     = "causes"
  val Enables    = "enables"
  val Triggers   = "triggers"
  val RespondsTo = "responds_to"
  val FallsWithin = "falls_within"
  val allen: Set[String] = Set(Before, Meets, Overlaps, During, Starts, Finishes, Equals)
  val causal: Set[String] = Set(Causes, Enables, Triggers, RespondsTo, FallsWithin)
  val all: List[String] = List(Before, Meets, Overlaps, During, Starts, Finishes, Equals,
    Causes, Enables, Triggers, RespondsTo, FallsWithin)

object DatingMethod:
  val all: List[String] = List("dendrochronology", "radiocarbon", "stratigraphy",
    "textual", "astronomical")

// ── DTOs ───────────────────────────────────────────────────────────────────────────────────────

/** Four-point time-span: uncertain bounds bracket the certain core. */
final case class TimeSpan(
    notBefore: Option[Long] = None,
    beginBy: Option[Long] = None,
    endFrom: Option[Long] = None,
    notAfter: Option[Long] = None
) derives KindlingsCodecAsObject
object TimeSpan:
  given Schema[TimeSpan] = KindlingsSchema.derived[TimeSpan].schema

/** Summary of one event (list results). */
final case class EventSummary(
    id: String,
    label: String,
    kind: String,
    timeSpan: TimeSpan,
    historicity: String,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    /** The scale the event HAS ([[EventScale.effective]]): stated, or derived. */
    scale: String = EventScale.Local,
    /** Whether [[scale]] is stated on the event (`false`: derived from its kind, place, geometry). */
    scaleStated: Boolean = false,
    /** [[EventTier]] (#1449). */
    tier: String = EventTier.Attested,
    /** How precisely it is dated ([[EventPrecision]]): `precise` | `approximate` | `imprecise` | `undated`. */
    precision: String = EventPrecision.Precise,
    /** The year its catalogue STATES, and the stated ± around it (GVP: `250 ± 2500`). */
    central: Option[Long] = None,
    plusMinus: Option[Long] = None,
    /** How it was dated (`Tephrochronology`, `Isotopic: K/Ar`…), as the catalogue says. */
    datingMethod: Option[String] = None
) derives KindlingsCodecAsObject:
  /** The stated dating in words — `250 CE ± 2500 (tephrochronology)` ([[EventPrecision.note]]). */
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)
object EventSummary:
  given Schema[EventSummary] = KindlingsSchema.derived[EventSummary].schema

/** Per-source dating of an event (the ChronOntology pattern). */
final case class EventDating(
    eventId: String,
    citekey: String,
    timeSpan: TimeSpan,
    method: Option[String] = None,
    note: Option[String] = None
) derives KindlingsCodecAsObject
object EventDating:
  given Schema[EventDating] = KindlingsSchema.derived[EventDating].schema

/** A temporal or causal edge between two events. */
final case class EventEdge(
    srcId: String,
    dstId: String,
    kind: String,
    citekey: String,
    note: Option[String] = None
) derives KindlingsCodecAsObject
object EventEdge:
  given Schema[EventEdge] = KindlingsSchema.derived[EventEdge].schema

/** An actor who participated in an event. */
final case class EventParticipant(
    eventId: String,
    actor: String,
    role: String = "participant",
    citekey: Option[String] = None
) derives KindlingsCodecAsObject
object EventParticipant:
  given Schema[EventParticipant] = KindlingsSchema.derived[EventParticipant].schema

/** Full detail view of one event (GET /api/events/{id}). */
final case class EventDetail(
    event: EventSummary,
    geometry: Option[String] = None,
    parent: Option[EventSummary] = None,
    children: List[EventSummary] = Nil,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    /** The Wikidata item the event is (`Q…`), when it is known — where its picture comes from. */
    wikidata: Option[String] = None,
    /** Pictures of the event from outside providers, the card's first (#1446). */
    media: List[MediaThumb] = Nil,
    /** `attested` (a source in the corpus attests it) or `catalogue` (a catalogue alone knows it) —
      * [[EventTier]], #1449. */
    tier: String = EventTier.Attested,
    /** Its other names (a catalogue's labels and aliases), searched for in the sources' text. */
    names: List[String] = Nil,
    /** The catalogues that list it, with their licence and citation. */
    catalogue: List[EventCatalogueRef] = Nil,
    /** The sources that attest it: its own `citekey` and the attestations naming it. */
    sources: List[EventSourceRef] = Nil,
    /** The corpus's findings near it in time and space (`events.links` distances), nearest first. */
    findings: List[EventFindingRef] = Nil,
    /** The articles that cite its attesting sources. */
    articles: List[EventArticleRef] = Nil,
    /** Sources that POSSIBLY discuss it — proposed, unconfirmed ([[EventMatcher]]). */
    candidates: List[EventLinkCandidate] = Nil,
    /** The questions it raises ([[EventLeads]]). */
    leads: List[EventLead] = Nil,
    /** Its fields a person corrected by hand, kept over the catalogue's on a re-import ([[Corrections]]). */
    corrections: List[Correction] = Nil
) derives KindlingsCodecAsObject
object EventDetail:
  given Schema[EventDetail] = KindlingsSchema.derived[EventDetail].schema

/** A source attesting an event: through the event's own `citekey` (`via = "citekey"`) or an
  * attestation (`via = "attestation"`, with its quote). */
final case class EventSourceRef(citekey: String, title: Option[String] = None, year: Option[Int] = None,
    via: String = "citekey", quote: Option[String] = None) derives KindlingsCodecAsObject
object EventSourceRef:
  given Schema[EventSourceRef] = KindlingsSchema.derived[EventSourceRef].schema

/** A finding near an event: how far (km) and how many years from its span (0 = overlapping). */
final case class EventFindingRef(id: String, citekey: String, claim: String, km: Double, yearGap: Long,
    periodStart: Option[Long] = None, periodEnd: Option[Long] = None) derives KindlingsCodecAsObject
object EventFindingRef:
  given Schema[EventFindingRef] = KindlingsSchema.derived[EventFindingRef].schema

/** An article citing one of an event's attesting sources. */
final case class EventArticleRef(slug: String, title: String, citekey: String) derives KindlingsCodecAsObject
object EventArticleRef:
  given Schema[EventArticleRef] = KindlingsSchema.derived[EventArticleRef].schema

/** Create/replace one event. */
final case class EventPut(
    label: String,
    kind: String = "event",
    timeSpan: TimeSpan = TimeSpan(),
    historicity: String = "attested",
    geometry: Option[String] = None,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    /** `local` | `regional`; absent (or `auto`) = derived ([[EventScale.effective]]). */
    scale: Option[String] = None
)
object EventPut:
  given Schema[EventPut] = KindlingsSchema.derived[EventPut].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EventPut] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPut])

/** Selective patch for one event. */
final case class EventPatch(
    label: Option[String] = None,
    kind: Option[String] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[String] = None,
    geometry: Option[String] = None,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    /** `local` | `regional` states the scale; `auto` clears it (derived again). */
    scale: Option[String] = None,
    /** The Wikidata item the event is (`Q…`); `""` clears it. */
    wikidata: Option[String] = None
)
object EventPatch:
  given Schema[EventPatch] = KindlingsSchema.derived[EventPatch].schema
  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EventPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPatch])

/** An event's own geometry from a point — what the CLI's `--lat/--lon` and the `patch_event` tool's
  * `lat`/`lon` write (GeoJSON, longitude first). */
object EventGeometry:
  /** Pure: the GeoJSON Point at (`lat`, `lon`), or why it is not one. */
  def point(lat: Double, lon: Double): Either[String, String] =
    if lat.isNaN || lat < -90 || lat > 90 then Left(s"lat $lat is not between -90 and 90")
    else if lon.isNaN || lon < -180 || lon > 180 then Left(s"lon $lon is not between -180 and 180")
    else Right(s"""{"type":"Point","coordinates":[${fmt(lon)},${fmt(lat)}]}""")

  /** Pure: the geometry a patch carries — `geometry` as given, or the point `lat`/`lon` (both or
    * neither). */
  def of(geometry: Option[String], lat: Option[Double], lon: Option[Double]): Either[String, Option[String]] =
    (geometry.map(_.trim).filter(_.nonEmpty), lat, lon) match
      case (Some(_), Some(_), _) | (Some(_), _, Some(_)) => Left("give geometry or lat/lon, not both")
      case (g @ Some(_), None, None)                      => Right(g)
      case (None, Some(a), Some(o))                       => point(a, o).map(Some(_))
      case (None, None, None)                             => Right(None)
      case _                                              => Left("lat and lon go together")

  // a plain decimal, never `1.0E-4`, and never with a locale's comma
  private def fmt(d: Double): String = BigDecimal(d).bigDecimal.stripTrailingZeros.toPlainString

/** Confirmation of event deletion. */
final case class EventDeleted(id: String, deleted: Boolean) derives KindlingsCodecAsObject
object EventDeleted:
  given Schema[EventDeleted] = KindlingsSchema.derived[EventDeleted].schema

/** List result for events. */
final case class EventList(
    total: Int,
    events: List[EventSummary],
    /** Where this page starts, and the offset of the next one (absent on the last) — [[OffsetPage]]. */
    offset: Int = 0,
    next: Option[Int] = None
) derives KindlingsCodecAsObject:
  def page: OffsetPage = OffsetPage(total, offset, events.size)
object EventList:
  given Schema[EventList] = KindlingsSchema.derived[EventList].schema
  def of(total: Int, rows: List[EventSummary], offset: Int): EventList =
    EventList(total, rows, offset, OffsetPage(total, offset, rows.size).next)

/** One event on the map (`GET /api/events/geo`, the playback layer): where to draw it and how.
  * `lat`/`lon` is its ANCHOR — its own point, else the point-on-surface of its own area, else its
  * place's point or the point-on-surface of the place's area. `outline` is the GeoJSON geometry of
  * the area a REGIONAL event covers (simplified for drawing), absent for a local event and for an
  * area known only by a point. `anchor` says where the position came from: `event` | `place`. */
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
) derives KindlingsCodecAsObject
object EventGeo:
  given Schema[EventGeo] = KindlingsSchema.derived[EventGeo].schema

  /** Pure: the years an event covers in every time view — its outer span (`notBefore` else `beginBy`,
    * to `notAfter` else `endFrom`). An OPEN-ENDED event (one end known, #1768) covers the end it states
    * alone — an event with only a start is an event at its start (its start window when `notBefore` and
    * `beginBy` are both known), as [[CatalogDating.span]] writes a catalogue's — never every year after
    * it: an event still going on states an end (the present year); nothing is inferred from its kind.
    * An undated one, nothing. The database's generated `events.outer_span` (V089) is the same rule
    * (`EventOuterSpanSuite` checks the two agree), so the map's feed and the web draw the same years. */
  def outer(ts: TimeSpan): Option[(Long, Long)] =
    val start = ts.notBefore.orElse(ts.beginBy).orElse(ts.endFrom).orElse(ts.notAfter)
    val end   = ts.notAfter.orElse(ts.endFrom).orElse(ts.beginBy).orElse(ts.notBefore)
    for a <- start; b <- end yield (a.min(b), a.max(b))

  /** Pure: what a card says of an open-ended event's missing side ([[outer]] drew it at the side it
    * states): `end not recorded`, `start not recorded`, or nothing (both known, or undated). */
  def missingEnd(ts: TimeSpan): Option[String] =
    val hasStart = ts.notBefore.isDefined || ts.beginBy.isDefined
    val hasEnd   = ts.endFrom.isDefined || ts.notAfter.isDefined
    if hasStart && !hasEnd then Some("end not recorded")
    else if hasEnd && !hasStart then Some("start not recorded")
    else None

/** The map's events: those with a position, how many of the listed ones have none, and how many are
  * too imprecisely dated to be drawn at a year (`imprecise`: [[EventPrecision]] — the events list
  * shows them). */
final case class EventGeoList(events: List[EventGeo], unplaced: Int = 0, imprecise: Int = 0) derives KindlingsCodecAsObject
object EventGeoList:
  given Schema[EventGeoList] = KindlingsSchema.derived[EventGeoList].schema
