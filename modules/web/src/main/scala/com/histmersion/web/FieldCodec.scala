package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.GeoPick
import com.histmersion.web.map.MapMotion
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import com.histmersion.api.automation.Agents
import com.histmersion.api.jobs.JobState

/** How a field's value crosses a string boundary — the URL hash (repeated keys for a list) or the browser's storage.
  * `decode` of nothing, or of garbage, is `None`: the field falls back to its default rather than to a half-parsed
  * value.
  */
trait FieldCodec[A]:
  def encode(a: A): List[com.histmersion.web.FieldCodec.Token]
  def decode(vs: List[com.histmersion.web.FieldCodec.Token]): Option[A]

  /** A list field MERGES an added hash value (`/#tag=x` adds a tag); a scalar one replaces. */
  def multi: Boolean = false

object FieldCodec:
  type Token = Token.T
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def clean(vs: List[com.histmersion.web.FieldCodec.Token]): List[String] = vs.map(_.value.trim).filter(_.nonEmpty)

  given optString: FieldCodec[Option[String]]:
    def encode(a: Option[String]) = a.toList.map(_.trim).filter(_.nonEmpty).map(Token(_))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).headOption)

  given optLong: FieldCodec[Option[Long]]:
    def encode(a: Option[Long]) = a.toList.map(l => Token(l.toString))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).headOption.flatMap(_.toLongOption))

  given strings: FieldCodec[List[String]]:
    def encode(a: List[String]) = a.map(Token(_))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).distinct)
    override def multi = true

  given bool: FieldCodec[Boolean]:
    def encode(a: Boolean) = if a then List(com.histmersion.web.FieldCodec.Token("true")) else Nil
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).headOption.contains("true"))

  given mapLayers: FieldCodec[Set[MapLayer]]:
    def encode(a: Set[MapLayer]) = MapLayer.values.toList.filter(a).map(l => Token(l.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).flatMap(t => MapLayer.fromWire(MapLayer.Wire(t))).toSet)
    override def multi = true

  given windowKeys: FieldCodec[List[WindowKey]]:
    def encode(a: List[WindowKey]) = a.map(k => Token(k.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).flatMap(t => WindowKey.parse(WindowKey.Wire(t))).distinct)
    override def multi = true

  given optJobState: FieldCodec[Option[JobState]]:
    def encode(a: Option[JobState]) = a.toList.map(j => Token(j.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).headOption.flatMap(t => JobState.parse(JobState.Wire(t))))

  given optRunState: FieldCodec[Option[Agents.RunState]]:
    def encode(a: Option[Agents.RunState]) = a.toList.map(r => Token(r.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = Some(clean(vs).headOption.flatMap(t => Agents.RunState.parse(com.histmersion.api.automation.Run.State.Wire(t))))

  given sheet: FieldCodec[Sheet]:
    def encode(a: Sheet) = List(Token(a.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = clean(vs).headOption.flatMap(Sheet.fromWire)

  given baseMap: FieldCodec[BaseMap]:
    def encode(a: BaseMap) = List(Token(a.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = clean(vs).headOption.flatMap(t => BaseMap.fromWire(BaseMap.Wire(t)))

  given mapMotion: FieldCodec[MapMotion]:
    def encode(a: MapMotion) = List(Token(a.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = clean(vs).headOption.flatMap(t => MapMotion.fromWire(MapMotion.Wire(t)))

  given geoPick: FieldCodec[GeoPick]:
    def encode(a: GeoPick) = List(Token(a.wire.value))
    def decode(vs: List[com.histmersion.web.FieldCodec.Token]) = clean(vs).headOption.flatMap(t => GeoPick.parse(GeoPick.Wire(t)))
