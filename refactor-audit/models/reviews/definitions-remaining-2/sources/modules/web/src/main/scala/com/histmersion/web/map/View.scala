package com.histmersion.web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** The view as the markers are placed in it, read once a frame. */
final case class View(surface: MapSurface, centreLon: View.CentreLon, w: View.W, h: View.H)

object View:
  type CentreLon = CentreLon.T
  object CentreLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type W = W.T
  object W extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type H = H.T
  object H extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(s: MapSurface): View =
    val (w, h) = s.size
    View(s, s.center.lon, w, h)
