package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class BasemapStatus(
    available: Boolean,
    dir: String,
    bbox: String,
    layers: List[BasemapLayerInfo],
    fetchCommand: String,
    freeBytes: Long = 0,
    running: Option[String] = None,
    hints: List[String] = Nil,
    styles: List[BasemapStyleState] = Nil,
    mapMaxZoom: Int = 16,
    regions: List[BasemapRegion] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def missing(id: String): List[String] = styles.find(_.id == id).fold(Nil)(_.missing)

object BasemapStatus:
  given Schema[BasemapStatus] = summon[KindlingsSchema[BasemapStatus]].schema
