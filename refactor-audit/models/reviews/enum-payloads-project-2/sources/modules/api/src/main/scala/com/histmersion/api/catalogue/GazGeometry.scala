package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GazGeometry:
  case GeoJson(text: GazGeometry.GeoJson.Text)
  case Wkt(text: GazGeometry.Wkt.Text)

object GazGeometry:
  object GeoJson:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Wkt:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
