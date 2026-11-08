package com.histmersion
package api.catalogue
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.{LatinFold, WireEnum}
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey

/** Pleiades snaps imprecise places to stand-in points: 17 places, three of them "Mesopotamia", share 37.5 N 37.5 E, and
  * some sit on the centre of an atlas grid square ([[LocationPrecision.GridCell]]). Such points rank below every
  * precise candidate.
  */
object Geocode:
  object TimeRank:
    type Class = Class.T
    object Class extends RefinedType[Int, Pure]
  object Placeholder:
    type Min = Min.T
    object Min extends RefinedType[Int, Pure]
  object Compass:
    type Fraction = Fraction.T
    object Fraction extends RefinedType[Double, Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends RefinedType[Double, Pure]
  type Key = Key.T
  object Key extends RefinedType[String, Pure]
  type KindRank = KindRank.T
  object KindRank extends RefinedType[Int, Pure]
  enum KindHint:
    case Site, Region, Polity, Water, Unknown

    def wire: KindHint.Wire = KindHint.Wire(toString.toLowerCase(java.util.Locale.ROOT))

  object KindHint:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
    /** Lenient: it parses a model's free answer, and an unrecognised word is no hint. */

    def parse(s: Wire): KindHint =
      val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
      values
        .find(_.wire == Wire(t))
        .getOrElse(t match
          case "city" | "town" | "settlement" | "village" | "temple" | "fort" | "sanctuary" | "cemetery" => Site
          case "country" | "area" | "province" | "landscape" | "valley" | "plain" | "island"             => Region
          case "state" | "kingdom" | "empire" | "polity" | "dynasty"                                     => Polity
          case "sea" | "lake" | "river" | "gulf" | "bay" | "strait"                                      => Water
          case _                                                                                         => Unknown)

    given WireEnumValues[KindHint]:
      def all: Array[KindHint] = values
      def wire(e: KindHint): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[KindHint] =
        values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
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

  def key(s: String): Key =
    val b = new StringBuilder((s).length)
    Aside.replaceAllIn((s), " ").foreach { c =>
      if Apostrophes.contains(c) then ()
      else if Separators.contains(c) then b += ' '
      else LatinFold.foldChar((c)).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then Key(spaced.drop(4)) else Key(spaced)

  enum Approximation:
    case Qualifier(dropped: Qualifier.Dropped)

    case OnlyWithin(missing: OnlyWithin.Missing)

    case Rough(places: Rough.Places)

    case GridSquare

    def describe: String = this match
      case Qualifier(d)  => (s"'$d' dropped")
      case OnlyWithin(m) => (s"'$m' not found there; the enclosing place's point")
      case Rough(n)      => (s"a placeholder point shared by $n places")
      case GridSquare    => ("the centre of an atlas grid square")

  object Approximation:
    object Qualifier:
      type Dropped = Dropped.T
      object Dropped extends RefinedType[String, Pure]

    object OnlyWithin:
      type Missing = Missing.T
      object Missing extends RefinedType[String, Pure]

    object Rough:
      type Places = Places.T
      object Places extends RefinedType[Int, Pure]


  final case class Attempt(name: Attempt.Name, within: Option[Attempt.Within] = None, approx: Option[Approximation] = None):
    def key: Key = Geocode.key(name.value)

  object Attempt:

    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Within = Within.T
    object Within extends RefinedType[String, Pure]

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

  def stripQualifier(name: Attempt.Name): Option[(Approximation.Qualifier.Dropped, Attempt.Name)] =
    val words = name.value.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: String) = QualifierWords.contains(key(w).value)
    @annotation.tailrec
    def go(rest: List[String], dropped: List[String]): (List[String], List[String]) = rest match
      case w :: tail if isQ(w)                                                            => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j).value) && isQ(w) => go(tail, dropped :+ j :+ w)
      case _                                                                              => (dropped, rest)
    val (dropped, rest) = go(words, Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && key(rest.mkString(" ")).value.nonEmpty)(
      (Approximation.Qualifier.Dropped(dropped.mkString(" ")), Attempt.Name(rest.mkString(" ")))
    )

  def compassBox(dropped: Approximation.Qualifier.Dropped): Option[(Compass.Fraction, Compass.Fraction, Compass.Fraction, Compass.Fraction)] =
    val words = dropped.value.split("[\\s\\-]+").map(w => key(w).value).filter(_.nonEmpty).toList
    val dirs = words.flatMap(w => Boxes.get(w.stripSuffix("ern")).orElse(Boxes.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  private def box(x0: Double, y0: Double, x1: Double, y1: Double) =
    (Compass.Fraction(x0), Compass.Fraction(y0), Compass.Fraction(x1), Compass.Fraction(y1))

  private val Boxes: Map[String, (Compass.Fraction, Compass.Fraction, Compass.Fraction, Compass.Fraction)] = Map(
    "north" -> box(0.0, 0.5, 1.0, 1.0),
    "south" -> box(0.0, 0.0, 1.0, 0.5),
    "east" -> box(0.5, 0.0, 1.0, 1.0),
    "west" -> box(0.0, 0.0, 0.5, 1.0),
    "northeast" -> box(0.5, 0.5, 1.0, 1.0),
    "northwest" -> box(0.0, 0.5, 0.5, 1.0),
    "southeast" -> box(0.5, 0.0, 1.0, 0.5),
    "southwest" -> box(0.0, 0.0, 0.5, 0.5),
    "central" -> box(0.25, 0.25, 0.75, 0.75),
    "mid" -> box(0.25, 0.25, 0.75, 0.75),
    "middle" -> box(0.25, 0.25, 0.75, 0.75)
  )

  private val Asides = "\\(([^)]*)\\)".r

  def attempts(name: Attempt.Name): List[Attempt] =
    val n = (name).value.trim
    if (key((n))).value.isEmpty then Nil
    else
      val asides = Asides.findAllMatchIn(n).map(_.group(1).trim).filter(a => (key((a))).value.nonEmpty).map(a => Attempt(Attempt.Name(a))).toList
      val comma = n.lastIndexOf(',') match
        case i if i > 0 && (key((n.take(i)))).value.nonEmpty && (key((n.drop(i + 1)))).value.nonEmpty =>
          Some((n.take(i).trim, n.drop(i + 1).trim))
        case _ => None
      val head = comma.fold(n)(_._1)
      val qualified =
        stripQualifier(Attempt.Name(head)).map((q, base) => Attempt(base, comma.map(c => Attempt.Within(c._2)), Some(Approximation.Qualifier(q))))
      val all = List(Attempt(Attempt.Name(n))) ++ asides ++
        comma.map((a, in) => Attempt(Attempt.Name(a), Some(Attempt.Within(in)))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(Attempt.Name(in), None, Some(Approximation.OnlyWithin(Approximation.OnlyWithin.Missing(a))))).toList
      all.distinctBy(a => (a.key, a.within.map(w => key(w.value))))

  final case class Candidate(
      id: Place.Id,
      label: Place.Label,
      kind: Candidate.Kind,
      byLabel: Candidate.ByLabel,
      from: Option[Year],
      to: Option[Year],
      dimension: Candidate.Dimension,
      area: Candidate.Area,
      shared: Approximation.Rough.Places,
      lon: Longitude,
      lat: Latitude,
      tier: Gazetteer.Rank = Gazetteer.Rank(0),
      gridCell: Candidate.GridCell = Candidate.GridCell(false)
  ):
    def undated: Boolean = (from.isEmpty && to.isEmpty)

    def span: Candidate.Span = Candidate.Span((for a <- from; b <- to yield (b).value - (a).value).getOrElse((Candidate.Span(Long.MaxValue / 4)).value))
    def overlaps(p: Period): Boolean = from.forall(_.value <= p.end.value) && to.forall(_.value >= p.start.value)

    def gap(p: Period): Candidate.Gap =
      if (overlaps(p)) then Candidate.Gap(0L)
      else
        to.filter(_.value < p.start.value)
          .map(t => Candidate.Gap(p.start.value - t.value))
          .orElse(from.filter(_.value > p.end.value).map(f => Candidate.Gap(f.value - p.end.value)))
          .getOrElse(Candidate.Gap(0L))

  object Candidate:
    type Gap = Gap.T
    object Gap extends RefinedType[Long, Pure]
    type Span = Span.T
    object Span extends RefinedType[Long, Pure]

    type Kind = Kind.T
    object Kind extends RefinedType[String, Pure]
    type ByLabel = ByLabel.T
    object ByLabel extends RefinedType[Boolean, Pure]
    type Dimension = Dimension.T
    object Dimension extends RefinedType[Int, Pure]
    type Area = Area.T
    object Area extends RefinedType[Double, Pure]
    type GridCell = GridCell.T
    object GridCell extends RefinedType[Boolean, Pure]

  private def broad(kind: Candidate.Kind) =
    kind.value == Place.Kind.Region || kind.value == Place.Kind.Polity

  def tierOf(importSource: Option[Place.Match.ImportSource]): Gazetteer.Rank = Gazetteer.rank(Place.Match(Place.Id(""), importSource))

  def isPlaceholder(c: Candidate, placeholderMin: Placeholder.Min): Boolean = c.dimension.value == 0 && c.shared.value >= placeholderMin.value

  private def kindRank(kind: Candidate.Kind, hint: KindHint): KindRank = hint match
    case KindHint.Site =>
      if kind.value == Place.Kind.Site then KindRank(0)
      else if kind.value == Place.Kind.Feature then KindRank(1)
      else KindRank(2)
    case KindHint.Water =>
      if kind.value == Place.Kind.Feature then KindRank(0)
      else if kind.value == Place.Kind.Site then KindRank(1)
      else KindRank(2)
    case _ => if broad(kind) then KindRank(0) else if kind.value == Place.Kind.Feature then KindRank(1) else KindRank(2)

  private def timeRank(c: Candidate, at: Option[Period]): (TimeRank.Class, Candidate.Gap) = at match
    case None    => if ((c.undated) == (true)) then (TimeRank.Class(0), Candidate.Gap(0L)) else (TimeRank.Class(1), Candidate.Gap(-(c.span).value))
    case Some(p) =>
      if ((c.undated) == (true)) then (TimeRank.Class(1), Candidate.Gap(0L))
      else if ((c.overlaps(p)) == (true)) then (TimeRank.Class(0), Candidate.Gap(0L))
      else (TimeRank.Class(2), c.gap(p))

  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: Placeholder.Min): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (
        if ((isPlaceholder(c, placeholderMin)) == (true)) || ((c.gridCell) == Candidate.GridCell(true)) then 1 else 0,
        if ((c.byLabel) == Candidate.ByLabel(true)) then 0 else 1,
        c.tier.value: Int,
        kindRank(c.kind, hint).value: Int,
        if broad(c.kind) && c.dimension.value == 0 then 1 else 0,
        t.value: Int,
        gap.value: Long,
        c.area.value: Double,
        c.id.value: String
      )
    }

  final case class Resolution(
      asked: Resolution.Asked,
      placeId: Place.Id,
      label: Place.Label,
      lon: Longitude,
      lat: Latitude,
      approximate: List[Approximation] = Nil
  ):
    def isApproximate: Boolean = (approximate.nonEmpty)

    def describe: String =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      (s"'$asked' → $label [$placeId]$approx")

  object Resolution:

    type Asked = Asked.T
    object Asked extends RefinedType[String, Pure]

  def choose(asked: Resolution.Asked, attempt: Attempt, ranked: List[Candidate], placeholderMin: Placeholder.Min): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(((isPlaceholder(c, placeholderMin)) == (true)))(Approximation.Rough(c.shared))
      val grid = Option.when(((c.gridCell) == Candidate.GridCell(true)) && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  def distanceKm(lon1: Longitude, lat1: Latitude, lon2: Longitude, lat2: Latitude): DistanceKm =
    val r = 6371.0
    val dLat = math.toRadians(((lat2).value - (lat1).value))
    val dLon = math.toRadians(((lon2).value - (lon1).value))
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians((lat1).value)) * math.cos(math.toRadians((lat2).value)) * math.pow(math.sin(dLon / 2), 2)
    DistanceKm(2 * r * math.asin(math.min(1.0, math.sqrt(a))))

  final case class Move(
      findingId: Finding.Id,
      citekey: Citekey,
      asked: Move.Asked,
      region: Option[Move.Region],
      fromLon: Move.FromLon,
      fromLat: Move.FromLat,
      to: Resolution
  ):
    def km: DistanceKm = distanceKm(fromLon, fromLat, to.lon, to.lat)

    def describe: String =
      val from = region.filter(_.value != asked.value).fold("")(r => s" (stored as '${r.value}')")
      String.format(
        java.util.Locale.ROOT,
        "%s: '%s'%s at %.2f N %.2f E → %s at %.2f N %.2f E, %.0f km%s",
        findingId.value,
        asked.value,
        from,
        fromLat.value,
        fromLon.value,
        to.label.value + " [" + to.placeId.value + "]",
        to.lat.value,
        to.lon.value,
        km.value,
        if ((to.isApproximate) == (true)) then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
      )

  object Move:
    type MinKm = MinKm.T
    object MinKm extends RefinedType[Double, Pure]

    type Asked = Asked.T
    object Asked extends RefinedType[String, Pure]
    type Region = Region.T
    object Region extends RefinedType[String, Pure]
    export com.histmersion.api.catalogue.{Longitude as FromLon, Latitude as FromLat}

  def worthMoving(m: Move, minKm: Move.MinKm): Boolean = (m.km).value > minKm.value

  final case class Placement(findingId: Finding.Id, citekey: Citekey, region: Placement.Region, to: Resolution):
    def describe: String =
      String.format(
        java.util.Locale.ROOT,
        "%s: '%s' → %s at %.2f N %.2f E%s",
        findingId.value,
        region.value,
        to.label.value + " [" + to.placeId.value + "]",
        to.lat.value,
        to.lon.value,
        if ((to.isApproximate) == (true)) then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
      )

  object Placement:

    type Region = Region.T
    object Region extends RefinedType[String, Pure]

  def periodOf(from: Option[Year], to: Option[Year]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(Year(math.min((a).value, (b).value)), Year(math.max((a).value, (b).value))))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
