package com.histmersion.api.catalogue
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.PlaceKind
import com.histmersion.WireEnumValues
import com.histmersion.api.{LatinFold, WireEnum}
import com.histmersion.api.given

/** Pleiades snaps imprecise places to stand-in points: 17 places, three of them "Mesopotamia", share 37.5 N 37.5 E, and
  * some sit on the centre of an atlas grid square ([[LocationPrecision.GridCell]]). Such points rank below every
  * precise candidate.
  */
object Geocode:

  enum KindHint:
    case Site, Region, Polity, Water, Unknown

    def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

  object KindHint:
    /** Lenient: it parses a model's free answer, and an unrecognised word is no hint. */
    def parse(s: String): KindHint =
      val t = s.trim.toLowerCase(java.util.Locale.ROOT)
      values
        .find(_.wire == t)
        .getOrElse(t match
          case "city" | "town" | "settlement" | "village" | "temple" | "fort" | "sanctuary" | "cemetery" => Site
          case "country" | "area" | "province" | "landscape" | "valley" | "plain" | "island"             => Region
          case "state" | "kingdom" | "empire" | "polity" | "dynasty"                                     => Polity
          case "sea" | "lake" | "river" | "gulf" | "bay" | "strait"                                      => Water
          case _                                                                                         => Unknown)

    given WireEnumValues[KindHint]:
      def all: Array[KindHint] = KindHint.values
      def wire(e: KindHint): String = e.wire
      def fromWire(s: String): Option[KindHint] =
        KindHint.values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
    given Encoder[KindHint] = WireEnum.encoder
    given Decoder[KindHint] = WireEnum.decoder
    given Schema[KindHint] = WireEnum.schema

  /** Must equal the separator list of the SQL function `histmersion_place_key`. */
  val Separators: Set[Char] =
    (" \u00a0\t\n\r!\"#$%&()*+,-./:;<=>?@[\\]^_{|}~" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u201c\u201d\u201e\u00ab\u00bb\u00b7\u2026").toSet

  /** Apostrophe-likes, dropped ("Rus'" = "Rus"). Must equal the SQL function `histmersion_place_key`'s. */
  val Apostrophes: Set[Char] = "'`\u00b4\u2018\u2019\u02bc\u02b9".toSet

  private val Aside = "\\([^)]*\\)".r

  def key(s: String): String =
    val b = new StringBuilder(s.length)
    Aside.replaceAllIn(s, " ").foreach { c =>
      if Apostrophes.contains(c) then ()
      else if Separators.contains(c) then b += ' '
      else LatinFold.foldChar(c).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then spaced.drop(4) else spaced

  enum Approximation:
    case Qualifier(dropped: String)

    case OnlyWithin(missing: String)

    case Rough(places: Int)

    case GridSquare

    def describe: String = this match
      case Qualifier(d)  => s"'$d' dropped"
      case OnlyWithin(m) => s"'$m' not found there; the enclosing place's point"
      case Rough(n)      => s"a placeholder point shared by $n places"
      case GridSquare    => "the centre of an atlas grid square"

  final case class Attempt(name: String, within: Option[String] = None, approx: Option[Approximation] = None):
    def key: String = Geocode.key(name)

  private val QualifierWords: Set[String] = Set(
    "north",
    "south",
    "east",
    "west",
    "northern",
    "southern",
    "eastern",
    "western",
    "northeast",
    "northwest",
    "southeast",
    "southwest",
    "northeastern",
    "northwestern",
    "southeastern",
    "southwestern",
    "central",
    "upper",
    "lower",
    "inner",
    "outer",
    "greater",
    "coastal",
    "interior",
    "middle",
    "mid",
    "highland",
    "lowland"
  )

  private val Joiners: Set[String] = Set("and", "or")

  def stripQualifier(name: String): Option[(String, String)] =
    val words = name.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: String) = QualifierWords.contains(key(w))
    @annotation.tailrec
    def go(rest: List[String], dropped: List[String]): (List[String], List[String]) = rest match
      case w :: tail if isQ(w)                                                      => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j)) && isQ(w) => go(tail, dropped :+ j :+ w)
      case _                                                                        => (dropped, rest)
    val (dropped, rest) = go(words, Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && key(rest.mkString(" ")).nonEmpty)(
      (dropped.mkString(" "), rest.mkString(" "))
    )

  def compassBox(dropped: String): Option[(Double, Double, Double, Double)] =
    val words = dropped.split("[\\s\\-]+").map(key).filter(_.nonEmpty).toList
    val dirs = words.flatMap(w => Compass.get(w.stripSuffix("ern")).orElse(Compass.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  private val Compass: Map[String, (Double, Double, Double, Double)] = Map(
    "north" -> (0.0, 0.5, 1.0, 1.0),
    "south" -> (0.0, 0.0, 1.0, 0.5),
    "east" -> (0.5, 0.0, 1.0, 1.0),
    "west" -> (0.0, 0.0, 0.5, 1.0),
    "northeast" -> (0.5, 0.5, 1.0, 1.0),
    "northwest" -> (0.0, 0.5, 0.5, 1.0),
    "southeast" -> (0.5, 0.0, 1.0, 0.5),
    "southwest" -> (0.0, 0.0, 0.5, 0.5),
    "central" -> (0.25, 0.25, 0.75, 0.75),
    "mid" -> (0.25, 0.25, 0.75, 0.75),
    "middle" -> (0.25, 0.25, 0.75, 0.75)
  )

  private val Asides = "\\(([^)]*)\\)".r

  def attempts(name: String): List[Attempt] =
    val n = name.trim
    if key(n).isEmpty then Nil
    else
      val asides = Asides.findAllMatchIn(n).map(_.group(1).trim).filter(a => key(a).nonEmpty).map(Attempt(_)).toList
      val comma = n.lastIndexOf(',') match
        case i if i > 0 && key(n.take(i)).nonEmpty && key(n.drop(i + 1)).nonEmpty =>
          Some((n.take(i).trim, n.drop(i + 1).trim))
        case _ => None
      val head = comma.fold(n)(_._1)
      val qualified =
        stripQualifier(head).map((q, base) => Attempt(base, comma.map(_._2), Some(Approximation.Qualifier(q))))
      val all = List(Attempt(n)) ++ asides ++
        comma.map((a, in) => Attempt(a, Some(in))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(in, None, Some(Approximation.OnlyWithin(a)))).toList
      all.distinctBy(a => (a.key, a.within.map(key)))

  final case class Candidate(
      id: String,
      label: String,
      kind: String,
      byLabel: Boolean,
      from: Option[Long],
      to: Option[Long],
      dimension: Int,
      area: Double,
      shared: Int,
      lon: Double,
      lat: Double,
      tier: Int = 0,
      gridCell: Boolean = false
  ):
    def undated: Boolean = from.isEmpty && to.isEmpty

    def span: Long = (for a <- from; b <- to yield b - a).getOrElse(Long.MaxValue / 4)
    def overlaps(p: Period): Boolean = from.forall(_ <= p.end) && to.forall(_ >= p.start)

    def gap(p: Period): Long =
      if overlaps(p) then 0L
      else to.filter(_ < p.start).map(p.start - _).orElse(from.filter(_ > p.end).map(_ - p.end)).getOrElse(0L)

  private def broad(kind: String) = kind == PlaceKind.Region || kind == PlaceKind.Polity

  def tierOf(importSource: Option[String]): Int = Gazetteer.rank(PlaceMatch("", importSource))

  def isPlaceholder(c: Candidate, placeholderMin: Int): Boolean = c.dimension == 0 && c.shared >= placeholderMin

  private def kindRank(kind: String, hint: KindHint): Int = hint match
    case KindHint.Site  => if kind == PlaceKind.Site then 0 else if kind == PlaceKind.Feature then 1 else 2
    case KindHint.Water => if kind == PlaceKind.Feature then 0 else if kind == PlaceKind.Site then 1 else 2
    case _              => if broad(kind) then 0 else if kind == PlaceKind.Feature then 1 else 2

  private def timeRank(c: Candidate, at: Option[Period]): (Int, Long) = at match
    case None    => if c.undated then (0, 0L) else (1, -c.span)
    case Some(p) =>
      if c.undated then (1, 0L)
      else if c.overlaps(p) then (0, 0L)
      else (2, c.gap(p))

  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: Int): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (
        if isPlaceholder(c, placeholderMin) || c.gridCell then 1 else 0,
        if c.byLabel then 0 else 1,
        c.tier,
        kindRank(c.kind, hint),
        if broad(c.kind) && c.dimension == 0 then 1 else 0,
        t,
        gap,
        c.area,
        c.id
      )
    }

  final case class Resolution(
      asked: String,
      placeId: String,
      label: String,
      lon: Double,
      lat: Double,
      approximate: List[Approximation] = Nil
  ):
    def isApproximate: Boolean = approximate.nonEmpty

    def describe: String =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      s"'$asked' → $label [$placeId]$approx"

  def choose(asked: String, attempt: Attempt, ranked: List[Candidate], placeholderMin: Int): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(isPlaceholder(c, placeholderMin))(Approximation.Rough(c.shared))
      val grid = Option.when(c.gridCell && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  def distanceKm(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double =
    val r = 6371.0
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) * math.pow(math.sin(dLon / 2), 2)
    2 * r * math.asin(math.min(1.0, math.sqrt(a)))

  final case class Move(
      findingId: String,
      citekey: String,
      asked: String,
      region: Option[String],
      fromLon: Double,
      fromLat: Double,
      to: Resolution
  ):
    def km: Double = distanceKm(fromLon, fromLat, to.lon, to.lat)

    def describe: String =
      val from = region.filter(_ != asked).fold("")(r => s" (stored as '$r')")
      String.format(
        java.util.Locale.ROOT,
        "%s: '%s'%s at %.2f N %.2f E → %s at %.2f N %.2f E, %.0f km%s",
        findingId,
        asked,
        from,
        fromLat,
        fromLon,
        to.label + " [" + to.placeId + "]",
        to.lat,
        to.lon,
        km,
        if to.isApproximate then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
      )

  def worthMoving(m: Move, minKm: Double): Boolean = m.km > minKm

  final case class Placement(findingId: String, citekey: String, region: String, to: Resolution):
    def describe: String =
      String.format(
        java.util.Locale.ROOT,
        "%s: '%s' → %s at %.2f N %.2f E%s",
        findingId,
        region,
        to.label + " [" + to.placeId + "]",
        to.lat,
        to.lon,
        if to.isApproximate then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
      )

  def periodOf(from: Option[Long], to: Option[Long]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(math.min(a, b), math.max(a, b)))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
