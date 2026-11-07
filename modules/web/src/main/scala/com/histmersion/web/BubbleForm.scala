package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js

/** A bubble's OUTLINE (#1647): the shape grammar's event square carried over to the clusters, so a cluster of events
  * reads apart from one of findings at a glance (docs/visualization-guide.md §2a).
  */
enum BubbleForm(val cls: BubbleForm.Cls):
  /** No events in it: the round count. */
  case Round extends BubbleForm(com.histmersion.web.BubbleForm.Cls(""))

  /** Events only: the count on a rounded SQUARE, its rings squares about the same centre. */
  case Events extends BubbleForm(com.histmersion.web.BubbleForm.Cls("hd-cluster--events"))

  /** Events among other kinds: the round count with a small square tab at its upper right. */
  case WithEvents extends BubbleForm(com.histmersion.web.BubbleForm.Cls("hd-cluster--with-events"))

object BubbleForm:
  type Cls = Cls.T
  object Cls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
