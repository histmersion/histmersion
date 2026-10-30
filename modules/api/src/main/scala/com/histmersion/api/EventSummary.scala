package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The stated dating in words — `250 CE ± 2500 (tephrochronology)` ([[EventPrecision.note]]). */
  def datingNote: Option[String] = EventPrecision.note(central, plusMinus, datingMethod)
object EventSummary:
  given Schema[EventSummary] = summon[KindlingsSchema[EventSummary]].schema
