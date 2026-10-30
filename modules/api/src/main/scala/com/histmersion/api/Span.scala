package com.histmersion.api

final case class Span(from: Option[Long], to: Option[Long]):
  def isEmpty: Boolean = from.isEmpty && to.isEmpty

object Span:
  val always: Span = Span(None, None)
