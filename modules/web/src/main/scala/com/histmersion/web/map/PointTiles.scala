package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** One of the server's point layers read as vector tiles (`GET /api/tiles/{layer}/{z}/{x}/{y}`): the map fetches,
  * caches and decodes the tiles of the view; the app reads their features and draws them as its own markers. Nothing is
  * drawn by the map itself.
  */
trait PointTiles:
  /** The URL template of the tiles (`{z}`, `{x}`, `{y}`), or none: no tile is asked for. A template the same as the one
    * set is no change; another one is fetched while the features of the old one stay readable until its tiles are in.
    */
  def setTiles(template: Option[com.histmersion.api.basemap.BasemapApi.TileTemplate]): Unit

  /** The features of the tiles of the view at its zoom, each once (a world copy, a tile's neighbour holding the same
    * item: one).
    */
  def features: Seq[TilePointFeature]

  /** `f` whenever the features may have changed: the tiles of a view, or of a new template, are in. */
  def onChange(f: () => Unit): Subscription

  /** Every tile of the view at the current template is in. */
  def loaded: Boolean

