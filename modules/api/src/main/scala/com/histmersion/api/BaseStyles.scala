package com.histmersion.api

/** The web map's two vector styles (docs/research-map-styles.md; the style documents are the web page's): what each
  * draws of the vector extract. `needs`: the data layers without which it is not itself — a style drawn from an extract
  * that left one out says so, and what to run. Physical's towns come from `places` when the extract has them; it is
  * whole without them.
  */
object BaseStyles:
  final case class Style(id: String, name: String, needs: List[String])

  val Readable: Style =
    Style("readable", "Readable", List("boundaries", "earth", "landcover", "landuse", "places", "roads", "water"))
  val Physical: Style = Style("physical", "Physical", List("earth", "landcover", "landuse", "water"))

  /** In the order the menu shows them; the first is the default. */
  val all: List[Style] = List(Readable, Physical)

  /** Each style against an extract without `dropped` (`None`: no extract — nothing is said to be missing, the whole map
    * is). Pure.
    */
  def states(dropped: Option[List[String]]): List[BasemapStyleState] =
    all.map(s => BasemapStyleState(s.id, s.name, dropped.fold(Nil)(d => Basemap.missingFor(s.needs, d))))
