package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/basemap`: is the terrain base map there, what of it, and how to get it. */
final case class BasemapStatus(
    /** The vector layer is there (the relief is optional: without it the map is flat). */
    available: Boolean,
    dir: String,
    /** The region the next fetch downloads (`basemap.bbox`). */
    bbox: String,
    layers: List[BasemapLayerInfo],
    /** The command that downloads it, for a page or a message to show. */
    fetchCommand: String,
    /** Free space where the files go, in bytes. */
    freeBytes: Long = 0,
    /** A fetch job running now. */
    running: Option[String] = None,
    /** What a fetch would add (the physical names to an extract made before they existed; the layers the street maps
      * need).
      */
    hints: List[String] = Nil,
    /** Each named style of the base-map menu and the vector data layers it draws that the stored extract lacks (none:
      * it can be drawn whole).
      */
    styles: List[BasemapStyleState] = Nil,
    /** How far the page zooms in on Readable and Physical (`basemap.map-max-zoom`). */
    mapMaxZoom: Int = 16,
    /** The named regions a fetch knows (`basemap.terrain.focus-regions`), for a page to offer. */
    regions: List[BasemapRegion] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  /** The style `id`'s missing layers: empty when it can be drawn whole or is not known. */
  def missing(id: String): List[String] = styles.find(_.id == id).fold(Nil)(_.missing)

object BasemapStatus:
  given Schema[BasemapStatus] = summon[KindlingsSchema[BasemapStatus]].schema
