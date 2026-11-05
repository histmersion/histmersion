package com.histmersion.web.marks

/** When something was written, as a person says it: "just now", "5 minutes ago", "3 days ago", or the date. Pure.
  */
object Ago:

  type ApplyNV = ApplyNV.T
  object ApplyNV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyNOne = ApplyNOne.T
  object ApplyNOne extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ApplyResult = ApplyResult.T
  object ApplyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyThenMs = ApplyThenMs.T
  object ApplyThenMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyNowMs = ApplyNowMs.T
  object ApplyNowMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyIsoDate = ApplyIsoDate.T
  object ApplyIsoDate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MsResult = MsResult.T
  object MsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MsIso = MsIso.T
  object MsIso extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfResult = OfResult.T
  object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfIso = OfIso.T
  object OfIso extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(thenMs: com.histmersion.web.marks.Ago.ApplyThenMs, nowMs: com.histmersion.web.marks.Ago.ApplyNowMs, isoDate: com.histmersion.web.marks.Ago.ApplyIsoDate): com.histmersion.web.marks.Ago.ApplyResult =
    val s = math.max(0.0, (nowMs - thenMs) / 1000)
    def n(v: com.histmersion.web.marks.Ago.ApplyNV, one: com.histmersion.web.marks.Ago.ApplyNOne) =
      val k = math.floor(v).toInt
      s"$k $one${if k == 1 then "" else "s"} ago"
    if s < 60 then "just now"
    else if s < 3600 then n(s / 60, "minute")
    else if s < 86400 then n(s / 3600, "hour")
    else if s < 86400 * 14 then n(s / 86400, "day")
    else isoDate.take(10)

  /** An ISO time's milliseconds, NaN for none. */
  def ms(iso: com.histmersion.web.marks.Ago.MsIso): com.histmersion.web.marks.Ago.MsResult = scala.scalajs.js.Date.parse(iso)

  def of(iso: com.histmersion.web.marks.Ago.OfIso): com.histmersion.web.marks.Ago.OfResult = apply(ms(iso), scala.scalajs.js.Date.now(), iso)
