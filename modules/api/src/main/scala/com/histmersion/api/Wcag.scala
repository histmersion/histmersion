package com.histmersion.api

/** WCAG 2.x contrast. */
object Wcag:
  type LuminanceLinResult = LuminanceLinResult.T
  object LuminanceLinResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LuminanceLinC = LuminanceLinC.T
  object LuminanceLinC extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LuminanceResult = LuminanceResult.T
  object LuminanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LuminanceR = LuminanceR.T
  object LuminanceR extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LuminanceG = LuminanceG.T
  object LuminanceG extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LuminanceB = LuminanceB.T
  object LuminanceB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexResultPart1 = HexResultPart1.T
  object HexResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexResultPart2 = HexResultPart2.T
  object HexResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexResultPart3 = HexResultPart3.T
  object HexResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HexS = HexS.T
  object HexS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastResult = ContrastResult.T
  object ContrastResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastAPart1 = ContrastAPart1.T
  object ContrastAPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastAPart2 = ContrastAPart2.T
  object ContrastAPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastAPart3 = ContrastAPart3.T
  object ContrastAPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastBPart1 = ContrastBPart1.T
  object ContrastBPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastBPart2 = ContrastBPart2.T
  object ContrastBPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContrastBPart3 = ContrastBPart3.T
  object ContrastBPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AaText = AaTextValue
  type AaTextValue = AaTextValue.T
  object AaTextValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def luminance(r: com.histmersion.api.Wcag.LuminanceR, g: com.histmersion.api.Wcag.LuminanceG, b: com.histmersion.api.Wcag.LuminanceB): com.histmersion.api.Wcag.LuminanceResult =
    def lin(c: com.histmersion.api.Wcag.LuminanceLinC): com.histmersion.api.Wcag.LuminanceLinResult =
      val v = (c).value / 255.0
      if v <= 0.03928 then _root_.com.histmersion.api.Wcag.LuminanceLinResult(v / 12.92) else _root_.com.histmersion.api.Wcag.LuminanceLinResult(math.pow((v + 0.055) / 1.055, 2.4))
    0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

  def hex(s: com.histmersion.api.Wcag.HexS): Option[(com.histmersion.api.Wcag.HexResultPart1, com.histmersion.api.Wcag.HexResultPart2, com.histmersion.api.Wcag.HexResultPart3)] =
    val h = (s).value.trim.stripPrefix("#")
    val full = if h.length == 3 then h.flatMap(c => s"$c$c") else h
    Option.when(full.length == 6 && full.forall(c => Character.digit(c, 16) >= 0))(
      (_root_.com.histmersion.api.Wcag.HexResultPart1(Integer.parseInt(full.take(2), 16)), _root_.com.histmersion.api.Wcag.HexResultPart2(Integer.parseInt(full.slice(2, 4), 16)), _root_.com.histmersion.api.Wcag.HexResultPart3(Integer.parseInt(full.drop(4), 16)))
    )

  def contrast(a: (com.histmersion.api.Wcag.ContrastAPart1, com.histmersion.api.Wcag.ContrastAPart2, com.histmersion.api.Wcag.ContrastAPart3), b: (com.histmersion.api.Wcag.ContrastBPart1, com.histmersion.api.Wcag.ContrastBPart2, com.histmersion.api.Wcag.ContrastBPart3)): com.histmersion.api.Wcag.ContrastResult =
    val la = luminance(_root_.com.histmersion.api.Wcag.LuminanceR((a._1).value), _root_.com.histmersion.api.Wcag.LuminanceG((a._2).value), _root_.com.histmersion.api.Wcag.LuminanceB((a._3).value))
    val lb = luminance(_root_.com.histmersion.api.Wcag.LuminanceR((b._1).value), _root_.com.histmersion.api.Wcag.LuminanceG((b._2).value), _root_.com.histmersion.api.Wcag.LuminanceB((b._3).value))
    (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05)

  /** AA for normal text; large text (≥ 18.66 px bold) needs 3. */
  val AaText: com.histmersion.api.Wcag.AaText = _root_.com.histmersion.api.Wcag.AaTextValue(4.5)
