package com.histmersion
package api
package time

import scala.scalajs.js

private[api] object IsoLocal:
  def text(instant: String): String =
    val at = new js.Date(instant)
    if at.getTime().isNaN then instant
    else
      f"${at.getFullYear().toInt}%04d-${at.getMonth().toInt + 1}%02d-${at.getDate().toInt}%02d " +
        f"${at.getHours().toInt}%02d:${at.getMinutes().toInt}%02d"
