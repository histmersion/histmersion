package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One downloaded layer, as the server finds it on disk. */
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
    /** Where it came from (the build file or the URL) and when it was fetched (ISO instant). */
    source: Option[String] = None,
    fetchedAt: Option[String] = None,
    /** Changes with every fetch: the tile URLs carry it, so a browser never shows a stale cached tile. */
    version: Option[String] = None,
    /** The credit line (HTML) the page shows with the layer. */
    attribution: String = "",
    /** What the extract kept ([[Basemap.keptLine]]): the vector layer's filter. */
    kept: Option[String] = None,
    /** The vector layer's data layers the download left out whole ([[Basemap.droppedLayers]]): a style drawing one of
      * them lacks it.
      */
    dropped: List[String] = Nil,
    /** Why the file is there but cannot be read. */
    error: Option[String] = None,
    /** The relief only: the deeper zooms read from the provider as a page needs them, and their cache. */
    onDemand: Option[BasemapOnDemand] = None,
    /** The relief only: the focus regions kept to a deeper zoom (`fetch --layer terrain --focus <name>`). */
    focus: List[BasemapFocus] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The deepest zoom the page can ask this layer for: the extract's, a focus region's, or the on-demand relief's.
    */
  def deepest: Option[Int] =
    (maxZoom.toList ++ focus.map(_.maxZoom) ++ onDemand.filter(_.enabled).map(_.maxZoom).toList).maxOption

object BasemapLayerInfo:
  given Schema[BasemapLayerInfo] = summon[KindlingsSchema[BasemapLayerInfo]].schema
