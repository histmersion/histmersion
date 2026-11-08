package com.histmersion
package web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** How far the sidebar is pulled up in the narrow (phone) layout, where it is a bottom sheet: a strip, half the screen,
  * or all of it but the search bar. The heights are the stylesheet's.
  */
enum Sheet(val wire: Sheet.Wire):
  case Peek extends Sheet(Sheet.Wire("peek"))
  case Half extends Sheet(Sheet.Wire("half"))
  case Full extends Sheet(Sheet.Wire("full"))

object Sheet:
  type Fraction = Fraction.T
  object Fraction extends Newtype[Double, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]


  def fromWire(s: String): Option[Sheet] = values.find(_.wire == Wire(s.trim.toLowerCase))

  /** A tap on the handle: one step up, and from the top back down to the strip. */
  def next(s: Sheet): Sheet = s match
    case Peek => Half
    case Half => Full
    case Full => Peek

  /** Where a drag lets go: the state whose height is nearest to `fraction` of the screen, given the strip's and the
    * full sheet's fractions. Pure.
    */
  def nearest(fraction: Fraction, peek: Fraction, full: Fraction): Sheet =
    List[(Sheet, Double)](Peek -> peek.value, Half -> 0.5, Full -> full.value).minBy((_, f) => math.abs(f - fraction.value))._1
