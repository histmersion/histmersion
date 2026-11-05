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
  def setTiles(template: Option[com.histmersion.web.map.PointTiles.SetTilesTemplate]): Unit

  /** The features of the tiles of the view at its zoom, each once (a world copy, a tile's neighbour holding the same
    * item: one).
    */
  def features: Seq[TilePointFeature]

  /** `f` whenever the features may have changed: the tiles of a view, or of a new template, are in. */
  def onChange(f: () => Unit): Subscription

  /** Every tile of the view at the current template is in. */
  def loaded: com.histmersion.web.map.PointTiles.LoadedResult

object PointTiles:
  type SetTilesTemplate = SetTilesTemplate.T
  object SetTilesTemplate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoadedResult = LoadedResult.T
  object LoadedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
