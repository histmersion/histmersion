package com.histmersion.plugin

import java.util.Locale

/** One HTTP answer as it came: its status, its headers (names in lower case, the first value of each) and its body.
  *
  * @param status
  *   the HTTP status, a slow-down (`429`) or an error answer included
  * @param headers
  *   the answer's headers, by lower-case name
  * @param body
  *   the answer's bytes
  */
final case class PluginHttpResponse(status: Int, headers: Map[String, String], body: Array[Byte]):
  /** A header by name, in any case. */
  def header(name: String): Option[String] = headers.get(name.toLowerCase(Locale.ROOT))
