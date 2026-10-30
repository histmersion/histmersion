package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A handler on the map, to take off. */
trait Subscription:
  def cancel(): Unit
