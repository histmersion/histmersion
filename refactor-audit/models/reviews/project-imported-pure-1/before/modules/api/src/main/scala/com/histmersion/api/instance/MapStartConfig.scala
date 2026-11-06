package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given

final case class MapStartConfig(
    mode: MapStartConfig.Mode = MapStartMode.Last.wire,
    lat: com.histmersion.api.catalogue.Latitude = 37.0,
    lon: com.histmersion.api.catalogue.Longitude = 30.0,
    zoom: MapStartConfig.Zoom = 5,
    label: MapStartConfig.Label = _root_.com.histmersion.api.instance.MapStartConfig.Label("")
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MapStartConfig:
  type Mode = Mode.T
  object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Zoom = Zoom.T
  object Zoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
