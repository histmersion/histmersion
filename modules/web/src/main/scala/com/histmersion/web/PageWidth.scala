package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** How wide a source's or an article's own page sets its text: at a reading width, or across the page. Kept per
  * browser.
  */
enum PageWidth(val wire: PageWidth.Wire, val label: PageWidth.Label):
  case Reading extends PageWidth(com.histmersion.web.PageWidth.Wire("reading"), com.histmersion.web.PageWidth.Label("Reading width"))
  case Wide extends PageWidth(com.histmersion.web.PageWidth.Wire("wide"), com.histmersion.web.PageWidth.Label("Page wide"))

object PageWidth:
  /** A stored value; anything else is the reading width. Pure. */
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def fromWire(s: com.histmersion.web.PageWidth.Wire): PageWidth = values.find(_.wire.value == Option(s).fold("")(_.value.trim.toLowerCase)).getOrElse(Reading)
