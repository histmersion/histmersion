package com.histmersion.api

object EventPrecision:
  val Precise = "precise"
  val Approximate = "approximate"
  val Imprecise = "imprecise"
  val Undated = "undated"
  val all: List[String] = List(Precise, Approximate, Imprecise, Undated)

  val PreciseUpTo: Long = 50
  val ApproximateUpTo: Long = 500

  /** Widths are taken in astronomical years: historical years have no year 0. */
  def uncertainty(ts: TimeSpan, plusMinus: Option[Long]): Option[Long] =
    EventGeo.outer(ts).map { (a, b) =>
      def a2(y: Long) = Years.toAstronomical(y)
      def width(x: Option[Long], y: Option[Long]): Long = (for p <- x; q <- y yield a2(q) - a2(p)).getOrElse(0L).max(0)
      val derived =
        if ts.beginBy.isEmpty && ts.endFrom.isEmpty then (a2(b) - a2(a)).abs / 2
        else width(ts.notBefore, ts.beginBy).max(width(ts.endFrom, ts.notAfter)) / 2
      derived.max(plusMinus.getOrElse(0L).max(0))
    }

  def of(uncertainty: Option[Long]): String =
    uncertainty match
      case None                            => Undated
      case Some(u) if u <= PreciseUpTo     => Precise
      case Some(u) if u <= ApproximateUpTo => Approximate
      case _                               => Imprecise

  def of(ts: TimeSpan, plusMinus: Option[Long]): String = of(uncertainty(ts, plusMinus))

  def mapped(precision: String): Boolean = precision != Imprecise && precision != Undated

  def label(precision: String): Option[String] =
    precision match
      case Imprecise   => Some("imprecise date")
      case Approximate => Some("approximate date")
      case _           => None

  def note(central: Option[Long], plusMinus: Option[Long], method: Option[String]): Option[String] =
    val m = method.map(_.trim).filter(_.nonEmpty).map(lowerFirst)
    central match
      case Some(c) => Some(YearFmt(c) + plusMinus.filter(_ > 0).fold("")(u => s" ± $u") + m.fold("")(x => s" ($x)"))
      case None    => m.map(x => s"dated by $x")

  private def lowerFirst(s: String): String =
    s.split(" ", -1)
      .map(w =>
        if w.length >= 2 && w.charAt(0).isUpper && w.charAt(1).isLower then s"${w.charAt(0).toLower}${w.substring(1)}"
        else w
      )
      .mkString(" ")
