package com.histmersion
package web

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** The four places of the top bar. Every page belongs to one of them, and its tab is the one marked. */
enum PlaceTab(val label: PlaceTab.Label):
  case Explore extends PlaceTab(PlaceTab.Label("Explore"))
  case Library extends PlaceTab(PlaceTab.Label("Library"))
  case Research extends PlaceTab(PlaceTab.Label("Research"))
  case Settings extends PlaceTab(PlaceTab.Label("Settings"))

object PlaceTab:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
