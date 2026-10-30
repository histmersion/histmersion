package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** A register that lists a place: the Wikidata property naming it and the place's id there. */
final case class PlaceRegister(property: String, id: String) derives KindlingsCodecAsObject, KindlingsSchema:
  /** The register's name, or the property when it is not one the extract keeps. */
  def name: String = PlaceRegister.names.getOrElse(property, property)

object PlaceRegister:
  /** The registers the Wikidata extract keeps, by property. */
  val names: Map[String, String] = Map(
    "P13741" -> "Archaeological Cadastre (Greece)",
    "P2186" -> "Wiki Loves Monuments",
    "P9951" -> "Greek Castles",
    "P8218" -> "Archaeology in Greece Online",
    "P8068" -> "ToposText",
    "P1936" -> "DARE (Digital Atlas of the Roman Empire)"
  )
