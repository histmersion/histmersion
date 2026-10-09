package com.histmersion.web

import com.histmersion.web.LabelPlacement.{Pt, Rect}

/** How a map name MOVES (#1447): pure, so the smoothing is a unit test and [[MapLabels]] only feeds it
  * the clock and writes the result.
  *
  *   - a name eases toward where it belongs with a critically damped spring ([[damp]]) — no overshoot,
  *     and a new target (the end of a pan computing a better place) only bends the path: position
  *     and velocity carry over, so nothing ever jumps;
  *   - it fades and grows in, and shrinks and fades out ([[fade]], [[appearScale]]) over [[FadeSec]];
  *   - during a zoom it scales with the map a little ([[zoomScale]]) and settles back at its size class
  *     once the zoom ends;
  *   - with reduced motion every position and size snaps, and only the fade remains. */
object LabelMotion:

  /** How quickly a name catches up with its target: the spring's smoothing time, seconds. */
  val SmoothSec = 0.18

  /** How long a name takes to fade in or out, seconds. */
  val FadeSec = 0.22

  /** A name appearing grows from this share of its size; one leaving shrinks to it. */
  val GrowFrom = 0.6

  /** How much of the map's own scaling a name follows during a zoom (0: none, 1: all of it). */
  val ZoomFollow = 0.3

  /** Closer than this (zoom-0 units of a screen pixel at the time) and slower, a name is at rest. */
  val RestPx = 0.05

  /** One axis of a critically damped spring, after `dt` seconds: the new value and velocity
    * (Game Programming Gems 4, 1.10 — exact for a fixed target, stable for any `dt`). */
  def damp(x: Double, v: Double, target: Double, dt: Double, smooth: Double = SmoothSec): (Double, Double) =
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
  final case class Motion(p: Pt, vx: Double, vy: Double):
    /** Toward `target` by `dt` seconds; snaps when `reduced`. */
    def toward(target: Pt, dt: Double, reduced: Boolean): Motion =
      if reduced then Motion(target, 0, 0)
      else
        val (x, vx1) = damp(p.x, vx, target.x, dt)
        val (y, vy1) = damp(p.y, vy, target.y, dt)
        Motion(Pt(x, y), vx1, vy1)

    /** Kept inside `inner` (the viewport inset by half the name): a name easing behind a pan is pushed
      * along by the screen edge instead of sliding under it — continuous, since the edge moves
      * continuously; its velocity is kept, so the easing carries on from there. */
    def within(inner: Rect): Motion =
      if inner.isEmpty || inner.contains(p) then this else copy(p = inner.clamp(p))

    /** At rest at `target`, within [[RestPx]] screen pixels at `scale`. */
    def restsAt(target: Pt, scale: Double): Boolean =
      math.abs(p.x - target.x) * scale < RestPx && math.abs(p.y - target.y) * scale < RestPx &&
        math.abs(vx) * scale < RestPx && math.abs(vy) * scale < RestPx

  object Motion:
    def at(p: Pt): Motion = Motion(p, 0, 0)

  /** The visibility `a` (0 hidden … 1 shown) after `dt` seconds of fading toward `shown`: linear, so a
    * name reversed mid-fade turns around where it is. */
  def fade(a: Double, shown: Boolean, dt: Double): Double =
    val step = if FadeSec <= 0 then 1.0 else dt / FadeSec
    if shown then math.min(1.0, a + step) else math.max(0.0, a - step)

  /** The size factor of a name at visibility `a`: it grows in from [[GrowFrom]] and shrinks out to it,
    * eased; with reduced motion it keeps its size and only fades. */
  def appearScale(a: Double, reduced: Boolean): Double =
    if reduced then 1.0
    else
      val e = 1 - (1 - a) * (1 - a)   // ease-out
      GrowFrom + (1 - GrowFrom) * e

  /** The size factor a name heads for while the map is at `zoom` and its size class was chosen at
    * `settled`: it follows [[ZoomFollow]] of the map's scaling, so a zoom grows or shrinks the names
    * with the map, and is 1 again once the zoom ends (then `settled == zoom`). */
  def zoomScale(zoom: Double, settled: Double, reduced: Boolean): Double =
    if reduced then 1.0 else math.pow(2, (zoom - settled) * ZoomFollow)

  /** The size factor that keeps a name's drawn width when its size class changes from one `before`
    * px wide to one `after` px wide — the class changes at the end of a zoom, and the name eases
    * from there to its new size instead of popping. */
  def rebase(s: Double, before: Double, after: Double, reduced: Boolean): Double =
    if reduced || before <= 0 || after <= 0 then 1.0 else s * before / after

  // ── the zoom animation ──────────────────────────────────────────────────────────────────────────

  /** A zoom in flight (Leaflet's CSS zoom animation): from the viewport `from` (zoom-0) at `z0` to the
    * one around `center` at `z1`, for a map `w` × `h` px. Leaflet scales the map about one fixed point,
    * the one at the same screen position in both views; [[at]] gives the view and the zoom at a moment. */
  final case class ZoomFlight(from: Rect, z0: Double, center: Pt, z1: Double, w: Double, h: Double):
    private val s1 = math.pow(2, z1 - z0)
    private val scale1 = math.pow(2, z1)
    val to: Rect = Rect(center.x - w / 2 / scale1, center.y - h / 2 / scale1, center.x + w / 2 / scale1,
      center.y + h / 2 / scale1)

    /** The view and the zoom at progress `t` (0…1, already eased). */
    def at(t: Double): (Rect, Double) =
      val tt = math.max(0.0, math.min(1.0, t))
      if math.abs(s1 - 1) < 1e-9 then
        // a pure move: slide
        def lerp(a: Double, b: Double) = a + (b - a) * tt
        (Rect(lerp(from.minX, to.minX), lerp(from.minY, to.minY), lerp(from.maxX, to.maxX), lerp(from.maxY, to.maxY)), z0)
      else
        val s = 1 + (s1 - 1) * tt
        val fx = (s1 * to.minX - from.minX) / (s1 - 1)
        val fy = (s1 * to.minY - from.minY) / (s1 - 1)
        (Rect(fx + (from.minX - fx) / s, fy + (from.minY - fy) / s, fx + (from.maxX - fx) / s, fy + (from.maxY - fy) / s),
          z0 + math.log(s) / math.log(2))

  /** Leaflet's zoom easing, `cubic-bezier(0, 0, .25, 1)` over its 0.25 s, at time fraction `u`. */
  def zoomEase(u: Double): Double =
    val x = math.max(0.0, math.min(1.0, u))
    // solve the bezier's x(t) = x by bisection, return y(t)
    def bx(t: Double) = 3 * (1 - t) * t * t * 0.25 + t * t * t
    def by(t: Double) = 3 * (1 - t) * (1 - t) * t * 0.0 + 3 * (1 - t) * t * t * 1.0 + t * t * t
    var lo = 0.0
    var hi = 1.0
    var i = 0
    while i < 30 do
      val mid = (lo + hi) / 2
      if bx(mid) < x then lo = mid else hi = mid
      i += 1
    by((lo + hi) / 2)

  /** Leaflet's zoom animation length, seconds (`.leaflet-zoom-anim .leaflet-zoom-animated`). */
  val ZoomSec = 0.25
