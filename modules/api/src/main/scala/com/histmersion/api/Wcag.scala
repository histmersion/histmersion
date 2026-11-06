package com.histmersion.api

/** WCAG 2.x contrast. */
object Wcag:
  type Channel = Channel.T
  object Channel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Hex = Hex.T
  object Hex extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinearChannel = LinearChannel.T
  object LinearChannel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Luminance = Luminance.T
  object Luminance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ratio = Ratio.T
  object Ratio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type AaText = AaTextValue

  def luminance(r: com.histmersion.api.Wcag.Channel, g: com.histmersion.api.Wcag.Channel, b: com.histmersion.api.Wcag.Channel): com.histmersion.api.Wcag.Luminance =
    def lin(c: com.histmersion.api.Wcag.Channel): com.histmersion.api.Wcag.LinearChannel =
      val v = (c).value / 255.0
      if v <= 0.03928 then _root_.com.histmersion.api.Wcag.LinearChannel(v / 12.92) else _root_.com.histmersion.api.Wcag.LinearChannel(math.pow((v + 0.055) / 1.055, 2.4))
    0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

  def hex(s: com.histmersion.api.Wcag.Hex): Option[(com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel)] =
    val h = (s).value.trim.stripPrefix("#")
    val full = if h.length == 3 then h.flatMap(c => s"$c$c") else h
    Option.when(full.length == 6 && full.forall(c => Character.digit(c, 16) >= 0))(
      (_root_.com.histmersion.api.Wcag.Channel(Integer.parseInt(full.take(2), 16)), _root_.com.histmersion.api.Wcag.Channel(Integer.parseInt(full.slice(2, 4), 16)), _root_.com.histmersion.api.Wcag.Channel(Integer.parseInt(full.drop(4), 16)))
    )

  def contrast(a: (com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel), b: (com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel)): com.histmersion.api.Wcag.Ratio =
    val la = luminance(_root_.com.histmersion.api.Wcag.Channel((a._1).value), _root_.com.histmersion.api.Wcag.Channel((a._2).value), _root_.com.histmersion.api.Wcag.Channel((a._3).value))
    val lb = luminance(_root_.com.histmersion.api.Wcag.Channel((b._1).value), _root_.com.histmersion.api.Wcag.Channel((b._2).value), _root_.com.histmersion.api.Wcag.Channel((b._3).value))
    (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05)

  /** AA for normal text; large text (≥ 18.66 px bold) needs 3. */
  val AaText: com.histmersion.api.Wcag.AaText = _root_.com.histmersion.api.Wcag.Ratio(4.5)
