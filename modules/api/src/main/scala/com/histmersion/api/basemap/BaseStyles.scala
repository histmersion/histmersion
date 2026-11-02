package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseStyles:
  final case class Style(id: String, name: String, needs: List[String])

  val Readable: Style =
    Style("readable", "Readable", List("boundaries", "earth", "landcover", "landuse", "places", "roads", "water"))
  val Physical: Style = Style("physical", "Physical", List("earth", "landcover", "landuse", "water"))

  val all: List[Style] = List(Readable, Physical)

  def states(dropped: Option[List[String]]): List[BasemapStyleState] =
    all.map(s => BasemapStyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
