package com.histmersion.api.catalogue

import com.histmersion.api.Span
import com.histmersion.api.given
final case class GazName(name: String, lang: String = "", span: Span = Span.always, citation: Option[String] = None)
