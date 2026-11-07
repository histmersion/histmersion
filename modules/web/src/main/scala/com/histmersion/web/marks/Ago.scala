package com.histmersion.web.marks

/** When something was written, as a person says it: "just now", "5 minutes ago", "3 days ago", or the date. Pure.
  */
object Ago:
  type IsoDate = IsoDate.T
  object IsoDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Millis = Millis.T
  object Millis extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Phrase = Phrase.T
  object Phrase extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(thenMs: com.histmersion.web.marks.Ago.Millis, nowMs: com.histmersion.web.marks.Ago.Millis, isoDate: com.histmersion.web.marks.Ago.IsoDate): com.histmersion.web.marks.Ago.Phrase =
    val s = math.max(0.0, (nowMs.value - thenMs.value) / 1000)
    def n(v: Double, one: String): Phrase =
      val k = math.floor(v).toInt
      Phrase(s"$k $one${if k == 1 then "" else "s"} ago")
    if s < 60 then Phrase("just now")
    else if s < 3600 then n(s / 60, "minute")
    else if s < 86400 then n(s / 3600, "hour")
    else if s < 86400 * 14 then n(s / 86400, "day")
    else Phrase(isoDate.value.take(10))

  /** An ISO time's milliseconds, NaN for none. */
  def ms(iso: com.histmersion.web.marks.Ago.IsoDate): com.histmersion.web.marks.Ago.Millis = Millis(scala.scalajs.js.Date.parse(iso.value))

  def of(iso: com.histmersion.web.marks.Ago.IsoDate): com.histmersion.web.marks.Ago.Phrase = apply(ms(iso), Millis(scala.scalajs.js.Date.now()), iso)
