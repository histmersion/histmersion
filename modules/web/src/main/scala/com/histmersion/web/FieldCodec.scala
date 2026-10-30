package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.map.MapMotion
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** How a field's value crosses a string boundary — the URL hash (repeated keys for a list) or the browser's storage.
  * `decode` of nothing, or of garbage, is `None`: the field falls back to its default rather than to a half-parsed
  * value.
  */
trait FieldCodec[A]:
  def encode(a: A): List[String]
  def decode(vs: List[String]): Option[A]

  /** A list field MERGES an added hash value (`/#tag=x` adds a tag); a scalar one replaces. */
  def multi: Boolean = false

object FieldCodec:
  private def clean(vs: List[String]) = vs.map(_.trim).filter(_.nonEmpty)

  given optString: FieldCodec[Option[String]]:
    def encode(a: Option[String]) = a.toList.map(_.trim).filter(_.nonEmpty)
    def decode(vs: List[String]) = Some(clean(vs).headOption)

  given optLong: FieldCodec[Option[Long]]:
    def encode(a: Option[Long]) = a.toList.map(_.toString)
    def decode(vs: List[String]) = Some(clean(vs).headOption.flatMap(_.toLongOption))

  given strings: FieldCodec[List[String]]:
    def encode(a: List[String]) = a
    def decode(vs: List[String]) = Some(clean(vs).distinct)
    override def multi = true

  given bool: FieldCodec[Boolean]:
    def encode(a: Boolean) = if a then List("true") else Nil
    def decode(vs: List[String]) = Some(clean(vs).headOption.contains("true"))

  given mapLayers: FieldCodec[Set[MapLayer]]:
    def encode(a: Set[MapLayer]) = MapLayer.values.toList.filter(a).map(_.wire)
    def decode(vs: List[String]) = Some(clean(vs).flatMap(MapLayer.fromWire).toSet)
    override def multi = true

  given windowKeys: FieldCodec[List[WindowKey]]:
    def encode(a: List[WindowKey]) = a.map(_.wire)
    def decode(vs: List[String]) = Some(clean(vs).flatMap(WindowKey.parse).distinct)
    override def multi = true

  given optJobState: FieldCodec[Option[JobState]]:
    def encode(a: Option[JobState]) = a.toList.map(_.wire)
    def decode(vs: List[String]) = Some(clean(vs).headOption.flatMap(JobState.parse))

  given optRunState: FieldCodec[Option[Agents.RunState]]:
    def encode(a: Option[Agents.RunState]) = a.toList.map(_.wire)
    def decode(vs: List[String]) = Some(clean(vs).headOption.flatMap(Agents.RunState.parse))

  given sheet: FieldCodec[Sheet]:
    def encode(a: Sheet) = List(a.wire)
    def decode(vs: List[String]) = clean(vs).headOption.flatMap(Sheet.fromWire)

  given baseMap: FieldCodec[BaseMap]:
    def encode(a: BaseMap) = List(a.wire)
    def decode(vs: List[String]) = clean(vs).headOption.flatMap(BaseMap.fromWire)

  given mapMotion: FieldCodec[MapMotion]:
    def encode(a: MapMotion) = List(a.wire)
    def decode(vs: List[String]) = clean(vs).headOption.flatMap(MapMotion.fromWire)

  given geoPick: FieldCodec[GeoPick]:
    def encode(a: GeoPick) = List(a.wire)
    def decode(vs: List[String]) = clean(vs).headOption.flatMap(GeoPick.parse)
