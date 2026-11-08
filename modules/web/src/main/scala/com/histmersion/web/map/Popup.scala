package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js

/** A popup at a map point, with the app's own markup. */
trait Popup:
  def close(): Unit
  def isOpen: Boolean
  def element: Option[dom.HTMLElement]

