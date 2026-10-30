package com.histmersion.api

/** One name of an imported record: language (`""` unknown), when it was used, and the citation a source gives for it
  * (an LPF `names[].citations`).
  */
final case class GazName(name: String, lang: String = "", span: Span = Span.always, citation: Option[String] = None)
