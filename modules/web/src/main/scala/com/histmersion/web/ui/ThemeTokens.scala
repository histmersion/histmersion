package com.histmersion.web.ui

import com.histmersion.api.Palette
import org.scalajs.dom

import scala.util.Try

/** The stylesheet's colour tokens, for the code that must COMPUTE with a colour rather than name it: the period ramp a
  * marker's colour is interpolated on, and the graph's canvas, which cannot see CSS. Values are read from `:root`'s
  * computed style once and cached; [[refresh]] drops the cache (a theme switch). Markup never comes here: it writes
  * `var(--token)`.
  */
object ThemeTokens:
  type CssText = CssText.T
  object CssText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Where a token's value comes from: the page's `:root`. A `var` so the tests, which run in Node with no stylesheet,
    * can supply the values instead.
    */

  private[web] var lookup: com.histmersion.web.ui.ThemeTokens.Name => Option[com.histmersion.web.ui.ThemeTokens.CssText] = fromDocument

  private def fromDocument(name: com.histmersion.web.ui.ThemeTokens.Name): Option[com.histmersion.web.ui.ThemeTokens.CssText] =
    Try(dom.window.getComputedStyle(dom.document.documentElement).getPropertyValue(name).trim).toOption
      .filter(_.nonEmpty)

  // `var`s: the caches, filled on first use and emptied by `refresh`
  private var cache = Map.empty[String, String]
  private var rampCache = Option.empty[Vector[(Int, Int, Int)]]

  def refresh(): Unit =
    cache = Map.empty
    rampCache = None

  /** A token's value, `""` when the stylesheet does not define it. */
  def apply(name: com.histmersion.web.ui.ThemeTokens.Name): com.histmersion.web.ui.ThemeTokens.CssText =
    cache.getOrElse(
      name, {
        val v = lookup(name).getOrElse("")
        cache += name -> v
        v
      }
    )

  /** `rgb(r,g,b)` / `#rrggbb` / `#rgb` → components; anything else is `None`. Pure. */
  def rgb(value: com.histmersion.api.Wcag.Hex): Option[(com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel)] =
    val v = value.trim
    val hex = if v.startsWith("#") then v.drop(1) else ""
    if hex.length == 6 && hex.forall(Character.digit(_, 16) >= 0) then
      Some(
        (Integer.parseInt(hex.take(2), 16), Integer.parseInt(hex.slice(2, 4), 16), Integer.parseInt(hex.drop(4), 16))
      )
    else if hex.length == 3 && hex.forall(Character.digit(_, 16) >= 0) then
      val List(r, g, b) = hex.toList.map(c => Integer.parseInt(s"$c$c", 16))
      Some((r, g, b))
    else
      "\\d+".r.findAllIn(v).take(3).map(_.toInt).toList match
        case r :: g :: b :: Nil if v.startsWith("rgb") => Some((r, g, b))
        case _                                         => None

  /** The period ramp's stops, in order. */
  def ramp: Vector[(com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel, com.histmersion.api.Wcag.Channel)] =
    rampCache.getOrElse {
      val stops = (0 until Palette.rampStops).toVector.flatMap(i => rgb(apply(s"--ramp-$i")))
      rampCache = Some(stops)
      stops
    }
