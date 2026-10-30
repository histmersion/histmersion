package com.histmersion.web

import scala.scalajs.js

/** The four places of the top bar. Every page belongs to one of them, and its tab is the one marked. */
enum PlaceTab(val label: String):
  case Explore extends PlaceTab("Explore")
  case Library extends PlaceTab("Library")
  case Research extends PlaceTab("Research")
  case Settings extends PlaceTab("Settings")
