package com.histmersion
package web
import com.histmersion.util.json.JsonCodec
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import hearth.kindlings.optics.syntax.*

import com.raquo.laminar.api.L.*
import com.histmersion.web.map.VectorStyle
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.json.NewtypeCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

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
) derives JsonCodec,
      CanEqual:

  /** Is the sea drawn by its depth in `style`: the viewer's choice, else the style's default. Pure. */
  def seaDepth(style: VectorStyle): Boolean = style match
    case VectorStyle.Physical => depthPhysical.fold(MapPrefs.depthByDefault(style))(_.shown)
    case VectorStyle.Readable => depthReadable.fold(MapPrefs.depthByDefault(style))(_.shown)

  /** The viewer's choice of the depth in `style`; the style's default is kept as no choice. Pure. */
  def withSeaDepth(style: VectorStyle, on: MapPrefs.SeaDepth): MapPrefs =
    val v = Option.when(on.shown != MapPrefs.depthByDefault(style))(on.shown)
    style match
      case VectorStyle.Physical =>
        this
          .modify(_.depthPhysical)
          .setTo(v.map((if _ then MapPrefs.DepthPhysical.Shown else MapPrefs.DepthPhysical.Hidden)))
      case VectorStyle.Readable =>
        this
          .modify(_.depthReadable)
          .setTo(v.map((if _ then MapPrefs.DepthReadable.Shown else MapPrefs.DepthReadable.Hidden)))

object MapPrefs:
  enum SeaDepth derives CanEqual:
    case Shown, Hidden
  object SeaDepth extends TwoStates[SeaDepth]:
    extension (seaDepth: SeaDepth) def shown: Boolean = seaDepth == Shown

  /** Physical is the map of the land's form, its sea coloured by depth; Readable keeps OSM Carto's flat water unless
    * the viewer asks for the depth.
    */
  enum Names derives CanEqual:
    case Shown, Hidden
  object Names extends TwoStates[Names]:
    extension (names: Names) def shown: Boolean = names == Shown
  type Language = Language.T
  object Language extends Newtype[String, Pure]
  enum Contours derives CanEqual:
    case Shown, Hidden
  object Contours extends TwoStates[Contours]:
    extension (contours: Contours) def shown: Boolean = contours == Shown
  enum DepthPhysical derives CanEqual:
    case Shown, Hidden
  object DepthPhysical extends TwoStates[DepthPhysical]:
    extension (depthPhysical: DepthPhysical) def shown: Boolean = depthPhysical == Shown
  enum DepthReadable derives CanEqual:
    case Shown, Hidden
  object DepthReadable extends TwoStates[DepthReadable]:
    extension (depthReadable: DepthReadable) def shown: Boolean = depthReadable == Shown

  def depthByDefault(style: VectorStyle): Boolean = style == VectorStyle.Physical

  /** Kept as one JSON value; anything unreadable is no options at all. */
  given FieldCodec[MapPrefs]:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def encode(a: MapPrefs) =
      if a == MapPrefs() then Nil else List(FieldCodec.Token(writeToString(a)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def decode(vs: List[FieldCodec.Token]) =
      Some(vs.headOption.flatMap(v => RawJson.decode[MapPrefs](v.value).toOption).getOrElse(MapPrefs()))
