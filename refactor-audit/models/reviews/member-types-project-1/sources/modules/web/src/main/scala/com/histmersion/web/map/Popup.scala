package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A popup at a map point, with the app's own markup. */
trait Popup:
  def close(): Unit
  def isOpen: com.histmersion.web.map.Popup.IsOpenResult
  def element: Option[dom.HTMLElement]

object Popup:
  type IsOpenResult = IsOpenResult.T
  object IsOpenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
