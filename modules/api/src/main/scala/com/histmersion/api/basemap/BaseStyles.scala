package com.histmersion.api.basemap
import com.histmersion.api.given
object BaseStyles:
  type StatesDropped = StatesDropped.T
  object StatesDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Style(id: Style.Id, name: Style.Name, needs: List[Style.Needs])

  object Style:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Needs = Needs.T
    object Needs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Readable: Style =
    Style("readable", "Readable", List("boundaries", "earth", "landcover", "landuse", "places", "roads", "water"))
  val Physical: Style = Style("physical", "Physical", List("earth", "landcover", "landuse", "water"))

  val all: List[Style] = List(Readable, Physical)

  def states(dropped: Option[List[com.histmersion.api.basemap.BaseStyles.StatesDropped]]): List[Basemap.StyleState] =
    all.map(s => Basemap.StyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
