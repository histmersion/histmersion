package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable

object NoMap:
  /** A shape set that draws nothing and reads back the ids it was given. */
  type EscS = EscS.T
  object EscS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessageResultPart1 = MessageResultPart1.T
  object MessageResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessageResultPart2 = MessageResultPart2.T
  object MessageResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessageWhy = MessageWhy.T
  object MessageWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlResult = HtmlResult.T
  object HtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HtmlWhy = HtmlWhy.T
  object HtmlWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final class Shapes(val id: Shapes.Id) extends ShapeSet:
    // `var`: the ids the app set, read back as it set them
    private var current: Seq[com.histmersion.web.map.NoMap.Shapes.Current] = Nil
    def set(shapes: Seq[ShapeFeature]): Unit = current = shapes.map(_.id)
    def restyle(id: com.histmersion.web.map.NoMap.Shapes.RestyleId, colour: com.histmersion.web.map.NoMap.Shapes.RestyleColour, alpha: com.histmersion.web.map.NoMap.Shapes.RestyleAlpha): Unit = ()
    def ids: Seq[com.histmersion.web.map.NoMap.Shapes.IdsResult] = current

  object Shapes:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Current = Current.T
    object Current extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestyleId = RestyleId.T
    object RestyleId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestyleColour = RestyleColour.T
    object RestyleColour extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestyleAlpha = RestyleAlpha.T
    object RestyleAlpha extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdsResult = IdsResult.T
    object IdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Tiles that are never asked for: no map, no features. */
  object Tiles extends PointTiles:
    type SetTilesTemplate = SetTilesTemplate.T
    object SetTilesTemplate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LoadedResult = LoadedResult.T
    object LoadedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def setTiles(template: Option[com.histmersion.web.map.NoMap.Tiles.SetTilesTemplate]): Unit = ()
    def features: Seq[TilePointFeature] = Nil
    def onChange(f: () => Unit): Subscription = new Subscription { def cancel(): Unit = () }
    def loaded: com.histmersion.web.map.NoMap.Tiles.LoadedResult = true

  private def esc(s: com.histmersion.web.map.NoMap.EscS) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** Pure: the message in the map's place — why, then what to do; the rest of the app works without it. */
  def message(why: com.histmersion.web.map.NoMap.MessageWhy): (com.histmersion.web.map.NoMap.MessageResultPart1, com.histmersion.web.map.NoMap.MessageResultPart2) =
    (
      s"The map cannot be drawn here: $why.",
      "Every base map — the online ones and the local ones — is drawn with WebGL 2. Turn on hardware " +
        "(graphics) acceleration in the browser's settings, or open the page in a current Chrome, Edge, " +
        "Firefox or Safari. The list, the timeline and the articles work without the map."
    )

  def html(why: com.histmersion.web.map.NoMap.HtmlWhy): com.histmersion.web.map.NoMap.HtmlResult =
    val (head, body) = message(why)
    s"""<p class="map-unavailable-title">${esc(head)}</p><p>${esc(body)}</p>"""
