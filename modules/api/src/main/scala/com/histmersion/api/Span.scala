package com.histmersion.api

final case class Span(from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year]):
  def isEmpty: Boolean = (from.isEmpty && to.isEmpty)

object Span:

  val always: Span = Span(None, None)
