package com.histmersion.web

import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The web UI's shared building blocks. Each thing the UI shows in more than one place — a source link, a chip, a
  * confidence mark, a layer switch, an icon — is built HERE, once, so the sidebar, the tabs, the map popup and the
  * graph cannot drift into five looks and three behaviours. The server's pages use the same class names and attributes
  * (`server.HtmlRender`).
  */

/** An overlay the map can show. The wire name is how it is persisted in [[UiState]]. */
enum MapLayer(val wire: MapLayer.Wire, val label: MapLayer.Label, val icon: Icon):
  case Boundaries extends MapLayer(com.histmersion.web.MapLayer.Wire("boundaries"), com.histmersion.web.MapLayer.Label("Boundaries"), Icon.Region)
  case Sites extends MapLayer(com.histmersion.web.MapLayer.Wire("sites"), com.histmersion.web.MapLayer.Label("Sites"), Icon.PointSite)
  case Media extends MapLayer(com.histmersion.web.MapLayer.Wire("media"), com.histmersion.web.MapLayer.Label("Media"), Icon.PointMedia)
  case Events extends MapLayer(com.histmersion.web.MapLayer.Wire("events"), com.histmersion.web.MapLayer.Label("Events"), Icon.PointEvent)

  /** The person's own notes left at places on the map (off until switched on). */
  case Notes extends MapLayer(com.histmersion.web.MapLayer.Wire("notes"), com.histmersion.web.MapLayer.Label("My notes"), Icon.Note)

object MapLayer:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def fromWire(s: com.histmersion.web.MapLayer.Wire): Option[MapLayer] = values.find(_.wire.value == s.value.trim.toLowerCase)
