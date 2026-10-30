package com.histmersion.api

final case class GazName(name: String, lang: String = "", span: Span = Span.always, citation: Option[String] = None)
