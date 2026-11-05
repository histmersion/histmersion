package com.histmersion.api

final case class Span(from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year]):
  def isEmpty: com.histmersion.api.Span.IsEmptyResult = from.isEmpty && to.isEmpty

object Span:
  type IsEmptyResult = IsEmptyResult.T
  object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val always: Span = Span(None, None)
