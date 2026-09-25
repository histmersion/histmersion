package com.histmersion.web

import com.histmersion.core.{Confidence, YearFmt, Label}
import com.histmersion.web.ui.{ViridisStops, ConfidenceSaturation}
import scala.math.*

object Fmt:

  def yr(y: Long): String = YearFmt(y)

  def yr(y: Option[Long]): String = y.map(yr).getOrElse("?")

  def periodStr(a: Option[Long], b: Option[Long]): String =
    (a, b) match
      case (Some(x), Some(y)) => if x == y then yr(x) else s"${yr(x)}–${yr(y)}"
      case _                  => ""

  def periodStr(a: Long, b: Long): String = periodStr(Some(a), Some(b))

  private val stops = ViridisStops.stops

  def color(t: Double): String =
    val ct = max(0.0, min(1.0, t))
    val f  = ct * (stops.length - 1)
    val i  = f.toInt
    val g  = f - i
    val a  = stops(i)
    val b  = stops(min(i + 1, stops.length - 1))
    def lerp(av: Int, bv: Int) = (av + (bv - av) * g).round.toInt
    s"rgb(${lerp(a._1, b._1)},${lerp(a._2, b._2)},${lerp(a._3, b._3)})"

  private val eraRe = "^(bce|bc|ce|ad)$".r
  private val numEraRe = "^(\\d+)(bce|bc|ce|ad)$".r

  def parseYear(raw: String): Option[Long] =
    if raw == null then None
    else
      val t = raw.trim.toLowerCase.replace(",", "").replace(".", " ")
        .replaceAll("^(c|ca|circa)\\s+", "").trim
      val parts = t.split("\\s+").filter(_.nonEmpty)
      if parts.isEmpty || parts.length > 2 then None
      else
        var era: Option[String] = None
        var num: Option[Long]   = None
        var bad = false
        for p <- parts if !bad do
          p match
            case eraRe(e) =>
              if era.isDefined then bad = true
              else era = Some(e)
            case s if s.matches("[+-]?\\d+") =>
              if num.isDefined then bad = true
              else num = Some(s.toLong)
            case numEraRe(n, e) =>
              era = Some(e)
              num = Some(n.toLong)
            case _ => bad = true
        if bad then None
        else num match
          case None => None
          case Some(n) =>
            era match
              case Some("bce") | Some("bc") => Some(-abs(n))
              case Some("ce") | Some("ad")  => Some(abs(n))
              case _                        => Some(n)

  private val confSat = ConfidenceSaturation.levels

  def desaturate(rgbStr: String, confidence: Confidence): String =
    val wire = confidence.toString.toLowerCase
    val s = confSat.getOrElse(wire, 1.0)
    if s >= 1.0 then return rgbStr
    val nums = "\\d+".r.findAllIn(rgbStr).toArray
    if nums.length < 3 then return rgbStr
    val r = nums(0).toInt; val g = nums(1).toInt; val b = nums(2).toInt
    val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
    def mix(v: Int) = (grey + (v - grey) * s).round.toInt
    s"rgb(${mix(r)},${mix(g)},${mix(b)})"

  private val confOrder = ConfidenceSaturation.order

  def worstConf(items: Iterable[Confidence]): Confidence =
    var worst = Confidence.High
    for c <- items do
      val cWire = c.toString.toLowerCase
      val wWire = worst.toString.toLowerCase
      if confOrder.getOrElse(cWire, 3) < confOrder.getOrElse(wWire, 3) then worst = c
    worst

  def display(s: String): String = Label.display(s)
  def displayTag(s: String): String = display(s)

  def esc(s: String): String =
    val sb = new StringBuilder(s.length)
    var i = 0
    while i < s.length do
      s.charAt(i) match
        case '&'  => sb.append("&amp;")
        case '<'  => sb.append("&lt;")
        case '>'  => sb.append("&gt;")
        case '"'  => sb.append("&quot;")
        case '\'' => sb.append("&#39;")
        case c    => sb.append(c)
      i += 1
    sb.toString

  def clip(text: String, maxLen: Int): String =
    val s = text.trim
    if s.length <= maxLen then s
    else
      val cut = s.substring(0, maxLen)
      val sp  = cut.lastIndexOf(' ')
      val base = if sp > maxLen * 0.6 then cut.substring(0, sp) else cut
      base.replaceAll("[\\s,;:.\\u2014-]+$", "") + "…"
