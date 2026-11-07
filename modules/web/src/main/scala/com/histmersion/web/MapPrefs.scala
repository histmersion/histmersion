package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.VectorStyle
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

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
    case VectorStyle.Physical => depthPhysical.getOrElse(MapPrefs.depthByDefault(style))
    case VectorStyle.Readable => depthReadable.getOrElse(MapPrefs.depthByDefault(style))

  /** The viewer's choice of the depth in `style`; the style's default is kept as no choice. Pure. */
  def withSeaDepth(style: VectorStyle, on: com.histmersion.web.MapPrefs.SeaDepth): MapPrefs =
    val v = Option.when(on != MapPrefs.depthByDefault(style))(on)
    style match
      case VectorStyle.Physical => this.modify(_.depthPhysical).setTo(v)
      case VectorStyle.Readable => this.modify(_.depthReadable).setTo(v)

object MapPrefs:
  type SeaDepth = SeaDepth.T
  object SeaDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stored = Stored.T
  object Stored extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** Physical is the map of the land's form, its sea coloured by depth; Readable keeps OSM Carto's flat water unless
    * the viewer asks for the depth.
    */

  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Language = Language.T
  object Language extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Contours = Contours.T
  object Contours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthPhysical = DepthPhysical.T
  object DepthPhysical extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DepthReadable = DepthReadable.T
  object DepthReadable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def depthByDefault(style: VectorStyle): Boolean = style == VectorStyle.Physical

  /** Kept as one JSON value; anything unreadable is no options at all. */
  given FieldCodec[MapPrefs]:
    def encode(a: MapPrefs) = if a == MapPrefs() then Nil else List(io.circe.syntax.EncoderOps(a).asJson.noSpaces)
    def decode(vs: List[com.histmersion.web.MapPrefs.Stored]) =
      Some(vs.headOption.flatMap(v => io.circe.parser.decode[MapPrefs](v).toOption).getOrElse(MapPrefs()))
