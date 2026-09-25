package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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
    citekey: Option[String] = None
) derives KindlingsCodecAsObject
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
    participants: List[EventParticipant] = Nil
) derives KindlingsCodecAsObject
object EventDetail:
  given Schema[EventDetail] = KindlingsSchema.derived[EventDetail].schema

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
    participants: List[EventParticipant] = Nil
) derives KindlingsCodecAsObject
object EventPut:
  given Schema[EventPut] = KindlingsSchema.derived[EventPut].schema

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
    participants: Option[List[EventParticipant]] = None
) derives KindlingsCodecAsObject
object EventPatch:
  given Schema[EventPatch] = KindlingsSchema.derived[EventPatch].schema

/** Confirmation of event deletion. */
final case class EventDeleted(id: String, deleted: Boolean) derives KindlingsCodecAsObject
object EventDeleted:
  given Schema[EventDeleted] = KindlingsSchema.derived[EventDeleted].schema

/** List result for events. */
final case class EventList(
    total: Int,
    events: List[EventSummary]
) derives KindlingsCodecAsObject
object EventList:
  given Schema[EventList] = KindlingsSchema.derived[EventList].schema
