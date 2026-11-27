package com.histmersion
package api.instance
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.ManualPlaceName

final case class MapStartConfig(
    mode: MapStartMode.Wire = MapStartMode.Last.wire,
    lat: Latitude = Latitude(37.0),
    lon: Longitude = Longitude(30.0),
    zoom: MapStartConfig.Zoom = MapStartConfig.Zoom(5.0),
    label: MapStartConfig.Label = MapStartConfig.Label("")
) derives JsonCodec,
      KindlingsSchema,
      CanEqual

object MapStartConfig:
  type Zoom = Zoom.T
  object Zoom extends Newtype[Double, Pure]
  export ManualPlaceName.Label
