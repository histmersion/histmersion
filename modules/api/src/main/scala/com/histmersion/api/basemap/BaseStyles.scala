package com.histmersion
package api.basemap
import com.histmersion.api.given
object BaseStyles:

  final case class Style(id: Basemap.StyleState.Id, name: Basemap.StyleState.Name, needs: List[Basemap.LayerInfo.Dropped])

  val Readable: Style =
    Style(Basemap.StyleState.Id("readable"), Basemap.StyleState.Name("Readable"), List(Basemap.LayerInfo.Dropped("boundaries"), Basemap.LayerInfo.Dropped("earth"), Basemap.LayerInfo.Dropped("landcover"), Basemap.LayerInfo.Dropped("landuse"), Basemap.LayerInfo.Dropped("places"), Basemap.LayerInfo.Dropped("roads"), Basemap.LayerInfo.Dropped("water")))
  val Physical: Style = Style(Basemap.StyleState.Id("physical"), Basemap.StyleState.Name("Physical"), List(Basemap.LayerInfo.Dropped("earth"), Basemap.LayerInfo.Dropped("landcover"), Basemap.LayerInfo.Dropped("landuse"), Basemap.LayerInfo.Dropped("water")))

  val all: List[Style] = List(Readable, Physical)

  def states(dropped: Option[List[Basemap.LayerInfo.Dropped]]): List[Basemap.StyleState] =
    all.map(s => Basemap.StyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
