package com.histmersion.api

enum GazGeometry:
  case GeoJson(text: String)
  case Wkt(text: String)
