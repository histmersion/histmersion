package com.histmersion.domain.catalogue.maptiles

/** A tile feature's property value, as the vector tile format types it. */
enum TileValue:
  case Text(value: String)
  case Whole(value: Long)
  case Real(value: Double)
  case Flag(value: Boolean)
