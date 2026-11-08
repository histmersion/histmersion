package com.histmersion
package web.ui

import com.histmersion.api.Palette
import org.scalajs.dom

import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wcag

/** The stylesheet's colour tokens, for the code that must COMPUTE with a colour rather than name it: the period ramp a
  * marker's colour is interpolated on, and the graph's canvas, which cannot see CSS. Values are read from `:root`'s
  * computed style once and cached; [[refresh]] drops the cache (a theme switch). Markup never comes here: it writes
  * `var(--token)`.
  */
object ThemeTokens:
  type CssText = CssText.T
  object CssText extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  /** Where a token's value comes from: the page's `:root`. A `var` so the tests, which run in Node with no stylesheet,
    * can supply the values instead.
    */

  private[web] var lookup: Name => Option[CssText] = fromDocument

  private def fromDocument(name: Name): Option[CssText] =
    Try(dom.window.getComputedStyle(dom.document.documentElement).getPropertyValue(name.value).trim).toOption
      .filter(_.nonEmpty)
      .map(CssText(_))

  // `var`s: the caches, filled on first use and emptied by `refresh`
  private var cache = Map.empty[Name, CssText]
  private var rampCache = Option.empty[Vector[(Wcag.Channel, Wcag.Channel, Wcag.Channel)]]

  def refresh(): Unit =
    cache = Map.empty
    rampCache = None

  /** A token's value, `""` when the stylesheet does not define it. */
  def apply(name: Name): CssText =
    cache.getOrElse(
      name, {
        val v = lookup(name).getOrElse(CssText(""))
        cache += name -> v
        v
      }
    )

  /** `rgb(r,g,b)` / `#rrggbb` / `#rgb` → components; anything else is `None`. Pure. */
  def rgb(value: Wcag.Hex): Option[(Wcag.Channel, Wcag.Channel, Wcag.Channel)] =
    val v = value.value.trim
    val hex = if v.startsWith("#") then v.drop(1) else ""
    if hex.length == 6 && hex.forall(Character.digit(_, 16) >= 0) then
      Some(
        (
          Wcag.Channel(Integer.parseInt(hex.take(2), 16)),
          Wcag.Channel(Integer.parseInt(hex.slice(2, 4), 16)),
          Wcag.Channel(Integer.parseInt(hex.drop(4), 16))
        )
      )
    else if hex.length == 3 && hex.forall(Character.digit(_, 16) >= 0) then
      val List(r, g, b) = hex.toList.map(c => Integer.parseInt(s"$c$c", 16))
      Some((Wcag.Channel(r), Wcag.Channel(g), Wcag.Channel(b)))
    else
      "\\d+".r.findAllIn(v).take(3).map(_.toInt).toList match
        case r :: g :: b :: Nil if v.startsWith("rgb") =>
          Some((Wcag.Channel(r), Wcag.Channel(g), Wcag.Channel(b)))
        case _                                         => None

  /** The period ramp's stops, in order. */
  def ramp: Vector[(Wcag.Channel, Wcag.Channel, Wcag.Channel)] =
    rampCache.getOrElse {
      val stops = (0 until Palette.rampStops.value).toVector.flatMap(i => rgb(Wcag.Hex(apply(Name(s"--ramp-$i")).value)))
      rampCache = Some(stops)
      stops
    }
