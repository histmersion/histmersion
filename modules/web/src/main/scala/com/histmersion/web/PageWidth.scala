package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** How wide a source's or an article's own page sets its text: at a reading width, or across the page. Kept per
  * browser.
  */
enum PageWidth(val wire: PageWidth.Wire, val label: PageWidth.Label) derives CanEqual:
  case Reading extends PageWidth(PageWidth.Wire("reading"), PageWidth.Label("Reading width"))
  case Wide extends PageWidth(PageWidth.Wire("wide"), PageWidth.Label("Page wide"))

object PageWidth:
  /** A stored value; anything else is the reading width. Pure. */
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fromWire(s: Wire): PageWidth =
    values.find(_.wire.value == Option(s).fold("")(_.value.trim.toLowerCase)).getOrElse(Reading)
