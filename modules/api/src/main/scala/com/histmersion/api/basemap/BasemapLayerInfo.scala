package com.histmersion.api.basemap
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BasemapLayerInfo(
    layer: String,
    present: Boolean,
    file: String,
    bytes: Long = 0,
    minZoom: Option[Int] = None,
    maxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    tiles: Option[Long] = None,
    tileType: Option[String] = None,
    source: Option[String] = None,
    fetchedAt: Option[String] = None,
    /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
    version: Option[String] = None,
    attribution: String = "",
    kept: Option[String] = None,
    dropped: List[String] = Nil,
    error: Option[String] = None,
    onDemand: Option[BasemapOnDemand] = None,
    focus: List[BasemapFocus] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def deepest: Option[Int] =
    (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(_.enabled).map(_.maxZoom).toList).maxOption

object BasemapLayerInfo:
  given Schema[BasemapLayerInfo] = summon[KindlingsSchema[BasemapLayerInfo]].schema
