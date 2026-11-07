package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable

object NoMap:
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessageText = MessageText.T
  object MessageText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A shape set that draws nothing and reads back the ids it was given. */
  type HtmlWhy = _root_.com.histmersion.web.map.NoMapSurface.Why
  val HtmlWhy: _root_.com.histmersion.web.map.NoMapSurface.Why.type = _root_.com.histmersion.web.map.NoMapSurface.Why

  final class Shapes(val id: Shapes.Id) extends ShapeSet:
    // `var`: the ids the app set, read back as it set them
    private var current: Seq[com.histmersion.web.map.NoMap.Shapes.Id] = Nil
    def set(shapes: Seq[ShapeFeature]): Unit = current = shapes.map(_.id)
    def restyle(id: com.histmersion.web.map.NoMap.Shapes.Id, colour: com.histmersion.web.map.NoMap.Shapes.RestyleColour, alpha: com.histmersion.web.map.NoMap.Shapes.RestyleAlpha): Unit = ()
    def ids: Seq[com.histmersion.web.map.NoMap.Shapes.Id] = current

  object Shapes:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type RestyleColour = RestyleColour.T
    object RestyleColour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestyleAlpha = RestyleAlpha.T
    object RestyleAlpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Tiles that are never asked for: no map, no features. */
  object Tiles extends PointTiles:

    def setTiles(template: Option[com.histmersion.api.basemap.BasemapApi.TileTemplate]): Unit = ()
    def features: Seq[TilePointFeature] = Nil
    def onChange(f: () => Unit): Subscription = new Subscription { def cancel(): Unit = () }
    def loaded: Boolean = true

  private def esc(s: com.histmersion.web.map.NoMap.Text) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** Pure: the message in the map's place — why, then what to do; the rest of the app works without it. */
  def message(why: com.histmersion.web.map.NoMapSurface.Why): (com.histmersion.web.map.NoMap.MessageText, com.histmersion.web.map.NoMap.MessageText) =
    (
      s"The map cannot be drawn here: $why.",
      "Every base map — the online ones and the local ones — is drawn with WebGL 2. Turn on hardware " +
        "(graphics) acceleration in the browser's settings, or open the page in a current Chrome, Edge, " +
        "Firefox or Safari. The list, the timeline and the articles work without the map."
    )

  def html(why: com.histmersion.web.map.NoMap.HtmlWhy): com.histmersion.web.map.NoMap.Html =
    val (head, body) = message(why)
    s"""<p class="map-unavailable-title">${esc(head)}</p><p>${esc(body)}</p>"""
