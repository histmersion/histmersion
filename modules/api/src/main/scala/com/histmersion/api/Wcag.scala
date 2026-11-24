package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** WCAG 2.x contrast. */
@movesTo(FeaturePlace.ApiUi)
object Wcag:
  type Channel = Channel.T
  object Channel extends Newtype[Int, Pure]
  type Hex = Hex.T
  object Hex extends Newtype[String, Pure]
  type LinearChannel = LinearChannel.T
  object LinearChannel extends Newtype[Double, Pure]
  type Luminance = Luminance.T
  object Luminance extends Newtype[Double, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def luminance(r: Channel, g: Channel, b: Channel): Luminance =
    def lin(c: Channel): LinearChannel =
      val v = (c).value / 255.0
      if v <= 0.03928 then LinearChannel(v / 12.92) else LinearChannel(math.pow((v + 0.055) / 1.055, 2.4))
    Luminance(0.2126 * lin(r).value + 0.7152 * lin(g).value + 0.0722 * lin(b).value)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def hex(s: Hex): Option[(Channel, Channel, Channel)] =
    val h = (s).value.trim.stripPrefix("#")
    val full = if h.length == 3 then h.flatMap(c => s"$c$c") else h
    Option.when(full.length == 6 && full.forall(c => Character.digit(c, 16) >= 0))(
      (
        Channel(Integer.parseInt(full.take(2), 16)),
        Channel(Integer.parseInt(full.slice(2, 4), 16)),
        Channel(Integer.parseInt(full.drop(4), 16))
      )
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def contrast(a: (Channel, Channel, Channel), b: (Channel, Channel, Channel)): Ratio =
    val la = luminance(a._1, a._2, a._3).value
    val lb = luminance(b._1, b._2, b._3).value
    Ratio((math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05))

  /** AA for normal text; large text (≥ 18.66 px bold) needs 3. */
  val AaText: Ratio = Ratio(4.5)
