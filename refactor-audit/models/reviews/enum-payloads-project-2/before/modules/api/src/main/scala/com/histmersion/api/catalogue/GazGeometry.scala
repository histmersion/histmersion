package com.histmersion.api.catalogue
import com.histmersion.api.given
enum GazGeometry:
  case GeoJson(text: String)
  case Wkt(text: String)
