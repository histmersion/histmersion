package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js

/** The marker SHAPE — what kind of thing a point is (docs/visualization-guide.md §2): a diamond a finding (a source's
  * claim), a circle a site, a square an event, a landscape photo print (a white mat) a picture.
  */
enum Shape(val cls: Shape.Cls):
  case Diamond extends Shape("diamond")
  case Circle extends Shape("circle")
  case Square extends Shape("square")
  case Photo extends Shape("photo")

object Shape:
  type Cls = Cls.T
  object Cls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
