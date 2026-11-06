package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseStyles:

  final case class Style(id: Basemap.StyleState.Id, name: Basemap.StyleState.Name, needs: List[Basemap.LayerInfo.Dropped])

  val Readable: Style =
    Style(_root_.com.histmersion.api.basemap.Basemap.StyleState.Id("readable"), _root_.com.histmersion.api.basemap.Basemap.StyleState.Name("Readable"), List(_root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("boundaries"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("earth"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("landcover"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("landuse"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("places"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("roads"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("water")))
  val Physical: Style = Style(_root_.com.histmersion.api.basemap.Basemap.StyleState.Id("physical"), _root_.com.histmersion.api.basemap.Basemap.StyleState.Name("Physical"), List(_root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("earth"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("landcover"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("landuse"), _root_.com.histmersion.api.basemap.Basemap.LayerInfo.Dropped("water")))

  val all: List[Style] = List(Readable, Physical)

  def states(dropped: Option[List[com.histmersion.api.basemap.Basemap.LayerInfo.Dropped]]): List[Basemap.StyleState] =
    all.map(s => Basemap.StyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
