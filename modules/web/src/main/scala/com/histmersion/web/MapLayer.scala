package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The web UI's shared building blocks. Each thing the UI shows in more than one place — a source link, a chip, a
  * confidence mark, a layer switch, an icon — is built HERE, once, so the sidebar, the tabs, the map popup and the
  * graph cannot drift into five looks and three behaviours. The server's pages use the same class names and attributes
  * (`server.HtmlRender`).
  */

/** An overlay the map can show. The wire name is how it is persisted in [[UiState]]. */
enum MapLayer(val wire: String, val label: String, val icon: Icon):
  case Boundaries extends MapLayer("boundaries", "Boundaries", Icon.Region)
  case Sites extends MapLayer("sites", "Sites", Icon.PointSite)
  case Media extends MapLayer("media", "Media", Icon.PointMedia)
  case Events extends MapLayer("events", "Events", Icon.PointEvent)

  /** The person's own notes left at places on the map (off until switched on). */
  case Notes extends MapLayer("notes", "My notes", Icon.Note)

object MapLayer:
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromWire(s: com.histmersion.web.MapLayer.FromWireS): Option[MapLayer] = values.find(_.wire == s.trim.toLowerCase)
