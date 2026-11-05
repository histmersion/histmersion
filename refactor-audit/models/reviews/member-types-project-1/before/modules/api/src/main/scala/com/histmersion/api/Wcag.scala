package com.histmersion.api

/** WCAG 2.x contrast. */
object Wcag:
  def luminance(r: Int, g: Int, b: Int): Double =
    def lin(c: Int): Double =
      val v = c / 255.0
      if v <= 0.03928 then v / 12.92 else math.pow((v + 0.055) / 1.055, 2.4)
    0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

  def hex(s: String): Option[(Int, Int, Int)] =
    val h = s.trim.stripPrefix("#")
    val full = if h.length == 3 then h.flatMap(c => s"$c$c") else h
    Option.when(full.length == 6 && full.forall(c => Character.digit(c, 16) >= 0))(
      (Integer.parseInt(full.take(2), 16), Integer.parseInt(full.slice(2, 4), 16), Integer.parseInt(full.drop(4), 16))
    )

  def contrast(a: (Int, Int, Int), b: (Int, Int, Int)): Double =
    val la = luminance(a._1, a._2, a._3)
    val lb = luminance(b._1, b._2, b._3)
    (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05)

  /** AA for normal text; large text (≥ 18.66 px bold) needs 3. */
  val AaText: Double = 4.5
