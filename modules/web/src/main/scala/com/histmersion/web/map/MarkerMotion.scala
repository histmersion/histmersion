package com.histmersion
package web.map

object MarkerMotion:

  final case class Motion(dx: Double, dy: Double, alpha: Double, scale: Double, running: Boolean)

  def bezier(x1: Double, y1: Double, x2: Double, y2: Double, t: Double): Double =
    if t <= 0 then 0.0
    else if t >= 1 then 1.0
    else
      def coord(a: Double, b: Double, s: Double): Double =
        3 * a * s * (1 - s) * (1 - s) + 3 * b * s * s * (1 - s) + s * s * s
      var lo = 0.0
      var hi = 1.0
      var s = t
      var i = 0
      while i < 30 do
        s = (lo + hi) / 2
        if coord(x1, x2, s) < t then lo = s else hi = s
        i += 1
      coord(y1, y2, s)

  private def easeOut(t: Double): Double = bezier(0, 0, 0.58, 1, t)
  private def easeIn(t: Double): Double = bezier(0.42, 0, 1, 1, t)

  def motion(present: Set[String], mx: Double, my: Double, elapsed: Double, still: Boolean): Motion =
    def p(ms: Double): Double = math.max(0.0, math.min(1.0, elapsed / ms))
    if still then Motion(0, 0, if present("hd-fade-out") || present("hd-exiting") then 0.0 else 1.0, 1.0, running = false)
    else
      var dx = 0.0
      var dy = 0.0
      var alpha = 1.0
      var scale = 1.0
      var running = false
      if present("hd-cluster-move") then
        val q = p(ClusterMotion.Ms.toDouble)
        running ||= q < 1
        if present("hd-cluster-ghost") then
          val e = bezier(0.4, 0, 1, 1, q)
          dx = mx * e
          dy = my * e
          alpha *= 1 - 0.65 * e
        else
          val e = bezier(0, 0, 0.25, 1, q)
          dx = mx * (1 - e)
          dy = my * (1 - e)
      if present("hd-cluster-arrive") then
        val q = p(ClusterMotion.Ms.toDouble)
        running ||= q < 1
        alpha *= (if q < 0.55 then 0.0 else easeOut((q - 0.55) / 0.45))
      if present("hd-fade-in") then
        val q = p(400)
        running ||= q < 1
        alpha *= easeOut(q)
      if present("hd-fade-out") then
        val q = p(350)
        running ||= q < 1
        alpha *= 1 - easeIn(q)
      if present("hd-entering") then
        val q = p(400)
        running ||= q < 1
        if q < 0.6 then
          val e = easeOut(q / 0.6)
          scale = 1.15 * e
          alpha *= e
        else scale = 1.15 - 0.15 * easeOut((q - 0.6) / 0.4)
      if present("hd-exiting") then
        val q = p(300)
        running ||= q < 1
        val e = easeIn(q)
        scale *= 1 - e
        alpha *= 1 - e
      Motion(dx, dy, alpha, scale, running)
