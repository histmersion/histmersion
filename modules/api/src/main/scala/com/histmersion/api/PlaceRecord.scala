package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** What a place's own record says beyond its names and dates ([[PlaceDetail.record]]): where its dates come from
  * (`dateSource`, e.g. `wikidata-p571`), its time periods' names, its heritage designations, and the registers that
  * list it. Empty for a place whose source states none of it.
  */
final case class PlaceRecord(
    dateSource: Option[String] = None,
    periods: List[String] = Nil,
    designations: List[PlaceDesignation] = Nil,
    registers: List[PlaceRegister] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def isEmpty: Boolean = dateSource.isEmpty && periods.isEmpty && designations.isEmpty && registers.isEmpty

object PlaceRecord:
  /** Pure: where a place's dates come from, in words — `wikidata-p580` "Wikidata: start and end time",
    * `p2348-periodo:<ids>` "its time period, dated by PeriodO".
    */
  def dateSourceText(s: String): String = s.trim.toLowerCase match
    case "wikidata-p580"                    => "Wikidata: its start and end time"
    case "wikidata-p571"                    => "Wikidata: its inception"
    case "wikidata-p576"                    => "Wikidata: when it was dissolved or destroyed"
    case p if p.startsWith("p2348-periodo") => "Wikidata: its time period, dated by PeriodO"
    case other                              => other
