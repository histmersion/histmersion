package com.histmersion
package web.map
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import org.scalajs.dom

import scala.collection.mutable
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{tolerated, LintRule}

object NoMap:
  type MessageText = MessageText.T
  object MessageText extends Newtype[String, Pure]

  /** A shape set that draws nothing and reads back the ids it was given. */
  final class Shapes(val id: Shapes.Id) extends ShapeSet:
    // `var`: the ids the app set, read back as it set them
    private var current: Seq[ShapeFeature.Id] = Nil
    def set(shapes: Seq[ShapeFeature]): Unit = current = shapes.map(_.id)
    def restyle(id: ShapeFeature.Id, colour: ShapeFeature.Colour, alpha: ShapeFeature.Alpha): Unit = ()
    def ids: Seq[ShapeFeature.Id] = current

  object Shapes:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  /** Tiles that are never asked for: no map, no features. */
  object Tiles extends PointTiles:

    def setTiles(template: Option[BasemapApi.TileTemplate]): Unit = ()
    def features: Seq[TilePointFeature] = Nil
    def onChange(f: () => Unit): Subscription = new Subscription { def cancel(): Unit = () }
    def loaded: Boolean = true
    def notifying: Boolean = false

  /** Pure: the message in the map's place — why, then what to do; the rest of the app works without it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def message(why: Problem): (MessageText, MessageText) =
    (
      MessageText(s"The map cannot be drawn here: ${why.sentence}."),
      MessageText(
        "Every base map — the online ones and the local ones — is drawn with WebGL 2. Turn on hardware " +
          "(graphics) acceleration in the browser's settings, or open the page in a current Chrome, Edge, " +
          "Firefox or Safari. The list, the timeline and the articles work without the map."
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def html(why: Problem): Html =
    val (head, body) = message(why)
    markup"""<p class="map-unavailable-title">$head</p><p>$body</p>"""
