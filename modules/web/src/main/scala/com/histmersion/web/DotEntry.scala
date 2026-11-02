package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** A clustered dot that is not a finding — a site, an event, a picture: what the bubble counts and colours, and the row
  * its list shows (its kind's icon, its name, a note, `mid` the middle of its dated span on the period ramp). A
  * finding's dot carries its [[GeoHit]]s instead.
  */
final case class DotEntry(
    kind: DotKind,
    id: String,
    label: String,
    icon: String,
    mid: Option[Double],
    note: String = ""
)
