package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseStyles:

  final case class Style(id: Style.Id, name: Style.Name, needs: List[Style.Needs])

  object Style:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Needs = Needs.T
    object Needs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Readable: Style =
    Style(_root_.com.histmersion.api.basemap.BaseStyles.Style.Id("readable"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Name("Readable"), List(_root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("boundaries"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("earth"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("landcover"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("landuse"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("places"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("roads"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("water")))
  val Physical: Style = Style(_root_.com.histmersion.api.basemap.BaseStyles.Style.Id("physical"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Name("Physical"), List(_root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("earth"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("landcover"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("landuse"), _root_.com.histmersion.api.basemap.BaseStyles.Style.Needs("water")))

  val all: List[Style] = List(Readable, Physical)

  def states(dropped: Option[List[com.histmersion.api.basemap.BaseStyles.Style.Id]]): List[Basemap.StyleState] =
    all.map(s => Basemap.StyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
