package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable

object NoMap:
  /** A shape set that draws nothing and reads back the ids it was given. */
  final class Shapes(val id: String) extends ShapeSet:
    // `var`: the ids the app set, read back as it set them
    private var current: Seq[String] = Nil
    def set(shapes: Seq[ShapeFeature]): Unit = current = shapes.map(_.id)
    def restyle(id: String, colour: String, alpha: Double): Unit = ()
    def ids: Seq[String] = current

  /** Tiles that are never asked for: no map, no features. */
  object Tiles extends PointTiles:
    def setTiles(template: Option[String]): Unit = ()
    def features: Seq[TilePointFeature] = Nil
    def onChange(f: () => Unit): Subscription = new Subscription { def cancel(): Unit = () }
    def loaded: Boolean = true

  private def esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** Pure: the message in the map's place — why, then what to do; the rest of the app works without it. */
  def message(why: String): (String, String) =
    (
      s"The map cannot be drawn here: $why.",
      "Every base map — the online ones and the local ones — is drawn with WebGL 2. Turn on hardware " +
        "(graphics) acceleration in the browser's settings, or open the page in a current Chrome, Edge, " +
        "Firefox or Safari. The list, the timeline and the articles work without the map."
    )

  def html(why: String): String =
    val (head, body) = message(why)
    s"""<p class="map-unavailable-title">${esc(head)}</p><p>${esc(body)}</p>"""
