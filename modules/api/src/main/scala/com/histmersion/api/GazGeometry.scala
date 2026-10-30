package com.histmersion.api

/** A record's shape: GeoJSON (an LPF feature) or WKT (an LP-TSV `geowkt`); PostGIS reads either. */
enum GazGeometry:
  case GeoJson(text: String)
  case Wkt(text: String)
