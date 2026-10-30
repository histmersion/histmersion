package com.histmersion.web.marks

/** When something was written, as a person says it: "just now", "5 minutes ago", "3 days ago", or the date.
  * Pure. */
object Ago:

  def apply(thenMs: Double, nowMs: Double, isoDate: String): String =
    val s = math.max(0.0, (nowMs - thenMs) / 1000)
    def n(v: Double, one: String) =
      val k = math.floor(v).toInt
      s"$k $one${if k == 1 then "" else "s"} ago"
    if s < 60 then "just now"
    else if s < 3600 then n(s / 60, "minute")
    else if s < 86400 then n(s / 3600, "hour")
    else if s < 86400 * 14 then n(s / 86400, "day")
    else isoDate.take(10)

  /** An ISO time's milliseconds, NaN for none. */
  def ms(iso: String): Double = scala.scalajs.js.Date.parse(iso)

  def of(iso: String): String = apply(ms(iso), scala.scalajs.js.Date.now(), iso)
