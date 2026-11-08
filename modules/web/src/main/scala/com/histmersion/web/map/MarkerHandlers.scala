package com.histmersion
package web.map

import org.scalajs.dom

import scala.collection.mutable
import scala.scalajs.js

/** A marker's answers to the pointer and the keyboard, dispatched by its pane: `over`/`out` once per entering and
  * leaving the marker (not its children), `focus`/`blur` for anything in it.
  */
trait MarkerHandlers:
  def over(e: dom.MouseEvent): Unit = ()
  def out(e: dom.MouseEvent): Unit = ()
  def move(e: dom.MouseEvent): Unit = ()
  def click(e: dom.MouseEvent): Unit = ()
  def focus(e: dom.FocusEvent): Unit = ()
  def blur(e: dom.FocusEvent): Unit = ()
  def key(e: dom.KeyboardEvent): Unit = ()
