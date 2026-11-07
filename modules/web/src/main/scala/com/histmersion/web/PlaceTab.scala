package com.histmersion.web

import scala.scalajs.js

/** The four places of the top bar. Every page belongs to one of them, and its tab is the one marked. */
enum PlaceTab(val label: PlaceTab.Label):
  case Explore extends PlaceTab(com.histmersion.web.PlaceTab.Label("Explore"))
  case Library extends PlaceTab(com.histmersion.web.PlaceTab.Label("Library"))
  case Research extends PlaceTab(com.histmersion.web.PlaceTab.Label("Research"))
  case Settings extends PlaceTab(com.histmersion.web.PlaceTab.Label("Settings"))

object PlaceTab:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
