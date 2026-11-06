package com.histmersion.web

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs.js

import org.scalajs.dom

import sttp.client4.*

/** The placeholders of a tile URL template ([[ApiClient.tileTemplate]]). Pure. */
object TilePlaceholder:
  type Z = ZValue
  type ZValue = ZValue.T
  object ZValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type X = XValue
  type XValue = XValue.T
  object XValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = YValue
  type YValue = YValue.T
  object YValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillResult = FillResult.T
  object FillResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FillUri = FillUri.T
  object FillUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Z: com.histmersion.web.TilePlaceholder.Z = 900000001
  val X: com.histmersion.web.TilePlaceholder.X = 900000002L
  val Y: com.histmersion.web.TilePlaceholder.Y = 900000003L

  /** `uri` with the placeholder tile replaced by `{z}/{x}/{y}`. */
  def fill(uri: com.histmersion.web.TilePlaceholder.FillUri): com.histmersion.web.TilePlaceholder.FillResult = uri.replace(s"/$Z/$X/$Y", "/{z}/{x}/{y}")
