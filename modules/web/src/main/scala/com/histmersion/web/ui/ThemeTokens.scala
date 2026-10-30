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

  /** Where a token's value comes from: the page's `:root`. A `var` so the tests, which run in Node with no stylesheet,
    * can supply the values instead.
    */
  private[web] var lookup: String => Option[String] = fromDocument

  private def fromDocument(name: String): Option[String] =
    Try(dom.window.getComputedStyle(dom.document.documentElement).getPropertyValue(name).trim).toOption
      .filter(_.nonEmpty)

  // `var`s: the caches, filled on first use and emptied by `refresh`
  private var cache = Map.empty[String, String]
  private var rampCache = Option.empty[Vector[(Int, Int, Int)]]

  def refresh(): Unit =
    cache = Map.empty
    rampCache = None

  /** A token's value, `""` when the stylesheet does not define it. */
  def apply(name: String): String =
    cache.getOrElse(
      name, {
        val v = lookup(name).getOrElse("")
        cache += name -> v
        v
      }
    )

  /** `rgb(r,g,b)` / `#rrggbb` / `#rgb` → components; anything else is `None`. Pure. */
  def rgb(value: String): Option[(Int, Int, Int)] =
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
  def ramp: Vector[(Int, Int, Int)] =
    rampCache.getOrElse {
      val stops = (0 until Palette.rampStops).toVector.flatMap(i => rgb(apply(s"--ramp-$i")))
      rampCache = Some(stops)
      stops
    }
