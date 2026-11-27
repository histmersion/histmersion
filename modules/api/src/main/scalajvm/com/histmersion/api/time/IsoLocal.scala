package com.histmersion
package api
package time

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import scala.util.Try

private[api] object IsoLocal:
  private val form = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  def text(instant: String): String =
    Try(form.format(Instant.parse(instant).atZone(ZoneId.systemDefault()))).getOrElse(instant)
