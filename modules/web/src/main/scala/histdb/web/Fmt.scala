package histdb.web

import scala.math.*

object Fmt:

  def yr(y: Long): String =
    if y < 0 then s"${-y} BCE"
    else if y == 0 then "1 BCE/1 CE"
    else s"$y CE"

  def yr(y: Option[Long]): String = y.map(yr).getOrElse("?")

  def periodStr(a: Option[Long], b: Option[Long]): String =
    (a, b) match
      case (Some(x), Some(y)) => if x == y then yr(x) else s"${yr(x)}–${yr(y)}"
      case _                  => ""

  def periodStr(a: Long, b: Long): String = periodStr(Some(a), Some(b))

  private val stops: Array[(Int, Int, Int)] =
    Array((68, 1, 84), (59, 82, 139), (33, 145, 140), (94, 201, 98), (253, 231, 37))

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

  private val confSat: Map[String, Double] =
    Map("high" -> 1.0, "medium" -> 0.7, "low" -> 0.4, "contested" -> 0.3)

  def desaturate(rgbStr: String, confidence: String): String =
    val s = confSat.getOrElse(confidence, 1.0)
    if s >= 1.0 then return rgbStr
    val nums = "\\d+".r.findAllIn(rgbStr).toArray
    if nums.length < 3 then return rgbStr
    val r = nums(0).toInt; val g = nums(1).toInt; val b = nums(2).toInt
    val grey = (0.299 * r + 0.587 * g + 0.114 * b).round.toInt
    def mix(v: Int) = (grey + (v - grey) * s).round.toInt
    s"rgb(${mix(r)},${mix(g)},${mix(b)})"

  private val confOrder: Map[String, Int] =
    Map("contested" -> 0, "low" -> 1, "medium" -> 2, "high" -> 3)

  def worstConf(items: Iterable[String]): String =
    var worst = "high"
    for c <- items do
      if confOrder.getOrElse(c, 3) < confOrder.getOrElse(worst, 3) then worst = c
    worst

  def displayTag(s: String): String = s.replace("_", " ")

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
