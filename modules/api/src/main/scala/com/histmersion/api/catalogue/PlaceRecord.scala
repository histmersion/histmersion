package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class PlaceRecord(
    dateSource: Option[String] = None,
    periods: List[String] = Nil,
    designations: List[PlaceDesignation] = Nil,
    registers: List[PlaceRegister] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def isEmpty: Boolean = dateSource.isEmpty && periods.isEmpty && designations.isEmpty && registers.isEmpty

object PlaceRecord:
  def dateSourceText(s: String): String = s.trim.toLowerCase match
    case "wikidata-p580"                    => "Wikidata: its start and end time"
    case "wikidata-p571"                    => "Wikidata: its inception"
    case "wikidata-p576"                    => "Wikidata: when it was dissolved or destroyed"
    case p if p.startsWith("p2348-periodo") => "Wikidata: its time period, dated by PeriodO"
    case other                              => other
