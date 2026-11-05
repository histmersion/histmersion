package com.histmersion.domain.catalogue.maptiles

/** One POINT feature of a vector tile: its place in tile units and its properties, in order. */
final case class TilePoint(x: Int, y: Int, props: Seq[(String, TileValue)])
