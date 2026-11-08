package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** The marker SHAPE — what kind of thing a point is (docs/visualization-guide.md §2): a diamond a finding (a source's
  * claim), a circle a site, a square an event, a landscape photo print (a white mat) a picture.
  */
enum Shape(val cls: Shape.Cls) derives CanEqual:
  case Diamond extends Shape(Shape.Cls("diamond"))
  case Circle extends Shape(Shape.Cls("circle"))
  case Square extends Shape(Shape.Cls("square"))
  case Photo extends Shape(Shape.Cls("photo"))

object Shape:
  type Cls = Cls.T
  object Cls extends Newtype[String, Pure]
