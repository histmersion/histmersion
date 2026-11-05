package com.histmersion.pluginhost

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** One HTTP answer: the status, the headers (names in lower case, first value) and the body. */
final case class HttpAnswer(code: Int, headers: Map[String, String], body: Array[Byte]):
  def header(name: String): Option[String] = headers.get(name.toLowerCase(Locale.ROOT))
  def text: String = new String(body, UTF_8)
