package com.histmersion.web

import hearth.kindlings.optics.syntax.*

import com.histmersion.web.LabelPlacement.{Pt, Rect}

/** How a map name MOVES (#1447): pure, so the smoothing is a unit test and [[MapLabels]] only feeds it the clock and
  * writes the result.
  *
  *   - a name eases toward where it belongs with a critically damped spring ([[damp]]) — no overshoot, and a new target
  *     (the end of a pan computing a better place) only bends the path: position and velocity carry over, so nothing
  *     ever jumps;
  *   - it fades and grows in, and shrinks and fades out ([[fade]], [[appearScale]]) over [[FadeSec]];
  *   - during a zoom it scales with the map a little ([[zoomScale]]) and settles back at its size class once the zoom
  *     ends;
  *   - with reduced motion every position and size snaps, and only the fade remains.
  */
object LabelMotion:

  /** How quickly a name catches up with its target: the spring's smoothing time, seconds. */
  type DampResultPart1 = DampResultPart1.T
  object DampResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampResultPart2 = DampResultPart2.T
  object DampResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampX = DampX.T
  object DampX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampV = DampV.T
  object DampV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampTarget = DampTarget.T
  object DampTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampDt = DampDt.T
  object DampDt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DampSmooth = DampSmooth.T
  object DampSmooth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeResult = FadeResult.T
  object FadeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeA = FadeA.T
  object FadeA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeShown = FadeShown.T
  object FadeShown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FadeDt = FadeDt.T
  object FadeDt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppearScaleResult = AppearScaleResult.T
  object AppearScaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppearScaleA = AppearScaleA.T
  object AppearScaleA extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AppearScaleReduced = AppearScaleReduced.T
  object AppearScaleReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomScaleResult = ZoomScaleResult.T
  object ZoomScaleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomScaleZoom = ZoomScaleZoom.T
  object ZoomScaleZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomScaleSettled = ZoomScaleSettled.T
  object ZoomScaleSettled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZoomScaleReduced = ZoomScaleReduced.T
  object ZoomScaleReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebaseResult = RebaseResult.T
  object RebaseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebaseS = RebaseS.T
  object RebaseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebaseBefore = RebaseBefore.T
  object RebaseBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebaseAfter = RebaseAfter.T
  object RebaseAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RebaseReduced = RebaseReduced.T
  object RebaseReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SmoothSec = 0.18

  /** How long a name takes to fade in or out, seconds. */
  val FadeSec = 0.22

  /** A name appearing grows from this share of its size; one leaving shrinks to it. */
  val GrowFrom = 0.6

  /** How much of the map's own scaling a name follows during a zoom (0: none, 1: all of it). */
  val ZoomFollow = 0.3

  /** Closer than this (zoom-0 units of a screen pixel at the time) and slower, a name is at rest. */
  val RestPx = 0.05

  /** One axis of a critically damped spring, after `dt` seconds: the new value and velocity (Game Programming Gems 4,
    * 1.10 — exact for a fixed target, stable for any `dt`).
    */
  def damp(x: com.histmersion.web.LabelMotion.DampX, v: com.histmersion.web.LabelMotion.DampV, target: com.histmersion.web.LabelMotion.DampTarget, dt: com.histmersion.web.LabelMotion.DampDt, smooth: com.histmersion.web.LabelMotion.DampSmooth = SmoothSec): (com.histmersion.web.LabelMotion.DampResultPart1, com.histmersion.web.LabelMotion.DampResultPart2) =
    if dt <= 0 then (x, v)
    else
      val omega = 2.0 / smooth
      val k = omega * dt
      val e = 1.0 / (1.0 + k + 0.48 * k * k + 0.235 * k * k * k)
      val change = x - target
      val temp = (v + omega * change) * dt
      val nv = (v - omega * temp) * e
      val nx = target + (change + temp) * e
      // never past the target (the approximation could graze it on a huge dt)
      if (target - x > 0) == (nx > target) then (target, 0.0) else (nx, nv)

  /** A point moving with its velocity (both zoom-0). */
  final case class Motion(p: Pt, vx: Motion.Vx, vy: Motion.Vy):
    /** Toward `target` by `dt` seconds; snaps when `reduced`. */
    def toward(target: Pt, dt: com.histmersion.web.LabelMotion.Motion.TowardDt, reduced: com.histmersion.web.LabelMotion.Motion.TowardReduced): Motion =
      if reduced then Motion(target, 0, 0)
      else
        val (x, vx1) = damp(p.x, vx, target.x, dt)
        val (y, vy1) = damp(p.y, vy, target.y, dt)
        Motion(Pt(x, y), vx1, vy1)

    /** Kept inside `inner` (the viewport inset by half the name): a name easing behind a pan is pushed along by the
      * screen edge instead of sliding under it — continuous, since the edge moves continuously; its velocity is kept,
      * so the easing carries on from there.
      */
    def within(inner: Rect): Motion =
      if inner.isEmpty || inner.contains(p) then this else this.modify(_.p).setTo(inner.clamp(p))

    /** At rest at `target`, within [[RestPx]] screen pixels at `scale`. */
    def restsAt(target: Pt, scale: com.histmersion.web.LabelMotion.Motion.RestsAtScale): com.histmersion.web.LabelMotion.Motion.RestsAtResult =
      math.abs(p.x - target.x) * scale < RestPx && math.abs(p.y - target.y) * scale < RestPx &&
        math.abs(vx) * scale < RestPx && math.abs(vy) * scale < RestPx

  object Motion:
    type TowardDt = TowardDt.T
    object TowardDt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TowardReduced = TowardReduced.T
    object TowardReduced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestsAtResult = RestsAtResult.T
    object RestsAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RestsAtScale = RestsAtScale.T
    object RestsAtScale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Vx = Vx.T
    object Vx extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Vy = Vy.T
    object Vy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def at(p: Pt): Motion = Motion(p, 0, 0)

  /** The visibility `a` (0 hidden … 1 shown) after `dt` seconds of fading toward `shown`: linear, so a name reversed
    * mid-fade turns around where it is.
    */
  def fade(a: com.histmersion.web.LabelMotion.FadeA, shown: com.histmersion.web.LabelMotion.FadeShown, dt: com.histmersion.web.LabelMotion.FadeDt): com.histmersion.web.LabelMotion.FadeResult =
    val step = if FadeSec <= 0 then 1.0 else dt / FadeSec
    if shown then math.min(1.0, a + step) else math.max(0.0, a - step)

  /** The size factor of a name at visibility `a`: it grows in from [[GrowFrom]] and shrinks out to it, eased; with
    * reduced motion it keeps its size and only fades.
    */
  def appearScale(a: com.histmersion.web.LabelMotion.AppearScaleA, reduced: com.histmersion.web.LabelMotion.AppearScaleReduced): com.histmersion.web.LabelMotion.AppearScaleResult =
    if reduced then 1.0
    else
      val e = 1 - (1 - a) * (1 - a) // ease-out
      GrowFrom + (1 - GrowFrom) * e

  /** The size factor a name heads for while the map is at `zoom` and its size class was chosen at `settled`: it follows
    * [[ZoomFollow]] of the map's scaling, so a zoom grows or shrinks the names with the map, and is 1 again once the
    * zoom ends (then `settled == zoom`).
    */
  def zoomScale(zoom: com.histmersion.web.LabelMotion.ZoomScaleZoom, settled: com.histmersion.web.LabelMotion.ZoomScaleSettled, reduced: com.histmersion.web.LabelMotion.ZoomScaleReduced): com.histmersion.web.LabelMotion.ZoomScaleResult =
    if reduced then 1.0 else math.pow(2, (zoom - settled) * ZoomFollow)

  /** The size factor that keeps a name's drawn width when its size class changes from one `before` px wide to one
    * `after` px wide — the class changes at the end of a zoom, and the name eases from there to its new size instead of
    * popping.
    */
  def rebase(s: com.histmersion.web.LabelMotion.RebaseS, before: com.histmersion.web.LabelMotion.RebaseBefore, after: com.histmersion.web.LabelMotion.RebaseAfter, reduced: com.histmersion.web.LabelMotion.RebaseReduced): com.histmersion.web.LabelMotion.RebaseResult =
    if reduced || before <= 0 || after <= 0 then 1.0 else s * before / after
