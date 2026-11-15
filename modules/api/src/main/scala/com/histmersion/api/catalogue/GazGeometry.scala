package com.histmersion
package api.catalogue
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
enum GazGeometry:
  case GeoJson(text: GeoJson.Text)
  case Wkt(text: Wkt.Text)

object GazGeometry:
  object GeoJson:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
  object Wkt:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
