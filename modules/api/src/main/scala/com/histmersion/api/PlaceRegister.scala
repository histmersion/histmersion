package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

final case class PlaceRegister(property: String, id: String) derives KindlingsCodecAsObject, KindlingsSchema:
  def name: String = PlaceRegister.names.getOrElse(property, property)

object PlaceRegister:
  val names: Map[String, String] = Map(
    "P13741" -> "Archaeological Cadastre (Greece)",
    "P2186" -> "Wiki Loves Monuments",
    "P9951" -> "Greek Castles",
    "P8218" -> "Archaeology in Greece Online",
    "P8068" -> "ToposText",
    "P1936" -> "DARE (Digital Atlas of the Roman Empire)"
  )
