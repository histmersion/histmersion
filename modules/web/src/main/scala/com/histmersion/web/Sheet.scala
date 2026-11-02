package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.VocabularyCodecs.given
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

/** How far the sidebar is pulled up in the narrow (phone) layout, where it is a bottom sheet: a strip, half the screen,
  * or all of it but the search bar. The heights are the stylesheet's.
  */
enum Sheet(val wire: String):
  case Peek extends Sheet("peek")
  case Half extends Sheet("half")
  case Full extends Sheet("full")

object Sheet:
  def fromWire(s: String): Option[Sheet] = values.find(_.wire == s.trim.toLowerCase)

  /** A tap on the handle: one step up, and from the top back down to the strip. */
  def next(s: Sheet): Sheet = s match
    case Peek => Half
    case Half => Full
    case Full => Peek

  /** Where a drag lets go: the state whose height is nearest to `fraction` of the screen, given the strip's and the
    * full sheet's fractions. Pure.
    */
  def nearest(fraction: Double, peek: Double, full: Double): Sheet =
    List(Peek -> peek, Half -> 0.5, Full -> full).minBy((_, f) => math.abs(f - fraction))._1
