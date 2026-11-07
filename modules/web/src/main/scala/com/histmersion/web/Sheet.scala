package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** How far the sidebar is pulled up in the narrow (phone) layout, where it is a bottom sheet: a strip, half the screen,
  * or all of it but the search bar. The heights are the stylesheet's.
  */
enum Sheet(val wire: Sheet.Wire):
  case Peek extends Sheet(com.histmersion.web.Sheet.Wire("peek"))
  case Half extends Sheet(com.histmersion.web.Sheet.Wire("half"))
  case Full extends Sheet(com.histmersion.web.Sheet.Wire("full"))

object Sheet:
  type Fraction = Fraction.T
  object Fraction extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def fromWire(s: String): Option[Sheet] = values.find(_.wire == s.trim.toLowerCase)

  /** A tap on the handle: one step up, and from the top back down to the strip. */
  def next(s: Sheet): Sheet = s match
    case Peek => Half
    case Half => Full
    case Full => Peek

  /** Where a drag lets go: the state whose height is nearest to `fraction` of the screen, given the strip's and the
    * full sheet's fractions. Pure.
    */
  def nearest(fraction: com.histmersion.web.Sheet.Fraction, peek: com.histmersion.web.Sheet.Fraction, full: com.histmersion.web.Sheet.Fraction): Sheet =
    List(Peek -> peek, Half -> 0.5, Full -> full).minBy((_, f) => math.abs(f - fraction))._1
