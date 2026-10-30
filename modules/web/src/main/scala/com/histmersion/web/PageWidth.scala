package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** How wide a source's or an article's own page sets its text: at a reading width, or across the page. Kept per
  * browser.
  */
enum PageWidth(val wire: String, val label: String):
  case Reading extends PageWidth("reading", "Reading width")
  case Wide extends PageWidth("wide", "Page wide")

object PageWidth:
  /** A stored value; anything else is the reading width. Pure. */
  def fromWire(s: String): PageWidth = values.find(_.wire == Option(s).fold("")(_.trim.toLowerCase)).getOrElse(Reading)
