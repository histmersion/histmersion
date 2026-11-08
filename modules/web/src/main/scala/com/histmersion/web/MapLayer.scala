package com.histmersion
package web

import com.raquo.laminar.api.L.*

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** The web UI's shared building blocks. Each thing the UI shows in more than one place — a source link, a chip, a
  * confidence mark, a layer switch, an icon — is built HERE, once, so the sidebar, the tabs, the map popup and the
  * graph cannot drift into five looks and three behaviours. The server's pages use the same class names and attributes
  * (`server.HtmlRender`).
  */

/** An overlay the map can show. The wire name is how it is persisted in [[UiState]]. */
enum MapLayer(val wire: MapLayer.Wire, val label: MapLayer.Label, val icon: Icon) derives CanEqual:
  case Boundaries extends MapLayer(MapLayer.Wire("boundaries"), MapLayer.Label("Boundaries"), Icon.Region)
  case Sites extends MapLayer(MapLayer.Wire("sites"), MapLayer.Label("Sites"), Icon.PointSite)
  case Media extends MapLayer(MapLayer.Wire("media"), MapLayer.Label("Media"), Icon.PointMedia)
  case Events extends MapLayer(MapLayer.Wire("events"), MapLayer.Label("Events"), Icon.PointEvent)

  /** The person's own notes left at places on the map (off until switched on). */
  case Notes extends MapLayer(MapLayer.Wire("notes"), MapLayer.Label("My notes"), Icon.Note)

object MapLayer:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]


  def fromWire(s: Wire): Option[MapLayer] = values.find(_.wire.value == s.value.trim.toLowerCase)
