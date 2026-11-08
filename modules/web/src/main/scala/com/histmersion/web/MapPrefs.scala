package com.histmersion
package web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.VectorStyle
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.json.NewtypeCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** A viewer's options over the vector styles ([[BaseMaps]]): names on or off, their language, the contours (Physical),
  * the sea's depth in each style (`depthPhysical`, `depthReadable`). `None`: the default (names on, English — or
  * `basemap.labels.language` — contours on, the depth on in Physical and off in Readable).
  */
final case class MapPrefs(
    names: Option[MapPrefs.Names] = None,
    language: Option[MapPrefs.Language] = None,
    contours: Option[MapPrefs.Contours] = None,
    depthPhysical: Option[MapPrefs.DepthPhysical] = None,
    depthReadable: Option[MapPrefs.DepthReadable] = None
) derives hearth.kindlings.circederivation.KindlingsCodecAsObject:

  /** Is the sea drawn by its depth in `style`: the viewer's choice, else the style's default. Pure. */
  def seaDepth(style: VectorStyle): Boolean = style match
    case VectorStyle.Physical => depthPhysical.fold(MapPrefs.depthByDefault(style))(_.value)
    case VectorStyle.Readable => depthReadable.fold(MapPrefs.depthByDefault(style))(_.value)

  /** The viewer's choice of the depth in `style`; the style's default is kept as no choice. Pure. */
  def withSeaDepth(style: VectorStyle, on: MapPrefs.SeaDepth): MapPrefs =
    val v = Option.when(on.value != MapPrefs.depthByDefault(style))(on.value)
    style match
      case VectorStyle.Physical => this.modify(_.depthPhysical).setTo(v.map(MapPrefs.DepthPhysical(_)))
      case VectorStyle.Readable => this.modify(_.depthReadable).setTo(v.map(MapPrefs.DepthReadable(_)))

object MapPrefs:
  type SeaDepth = SeaDepth.T
  object SeaDepth extends Newtype[Boolean, Pure]
  /** Physical is the map of the land's form, its sea coloured by depth; Readable keeps OSM Carto's flat water unless
    * the viewer asks for the depth.
    */

  type Names = Names.T
  object Names extends Newtype[Boolean, Pure]
  type Language = Language.T
  object Language extends Newtype[String, Pure]
  type Contours = Contours.T
  object Contours extends Newtype[Boolean, Pure]
  type DepthPhysical = DepthPhysical.T
  object DepthPhysical extends Newtype[Boolean, Pure]
  type DepthReadable = DepthReadable.T
  object DepthReadable extends Newtype[Boolean, Pure]

  def depthByDefault(style: VectorStyle): Boolean = style == VectorStyle.Physical

  /** Kept as one JSON value; anything unreadable is no options at all. */
  given FieldCodec[MapPrefs]:
    def encode(a: MapPrefs) = if a == MapPrefs() then Nil else List(FieldCodec.Token(io.circe.syntax.EncoderOps(a).asJson.noSpaces))
    def decode(vs: List[FieldCodec.Token]) =
      Some(vs.headOption.flatMap(v => io.circe.parser.decode[MapPrefs](v.value).toOption).getOrElse(MapPrefs()))
