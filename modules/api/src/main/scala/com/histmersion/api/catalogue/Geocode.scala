package com.histmersion.api.catalogue
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.{LatinFold, WireEnum}
import com.histmersion.api.given

/** Pleiades snaps imprecise places to stand-in points: 17 places, three of them "Mesopotamia", share 37.5 N 37.5 E, and
  * some sit on the centre of an atlas grid square ([[LocationPrecision.GridCell]]). Such points rank below every
  * precise candidate.
  */
object Geocode:
  object TimeRank:
    type Class = Class.T
    object Class extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Placeholder:
    type Min = Min.T
    object Min extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Compass:
    type Fraction = Fraction.T
    object Fraction extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindRank = KindRank.T
  object KindRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  enum KindHint:
    case Site, Region, Polity, Water, Unknown

    def wire: com.histmersion.api.catalogue.Geocode.KindHint.Wire = _root_.com.histmersion.api.catalogue.Geocode.KindHint.Wire(toString.toLowerCase(java.util.Locale.ROOT))

  object KindHint:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    /** Lenient: it parses a model's free answer, and an unrecognised word is no hint. */

    def parse(s: com.histmersion.api.catalogue.Geocode.KindHint.Wire): KindHint =
      val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
      values
        .find(_.wire == _root_.com.histmersion.api.catalogue.Geocode.KindHint.Wire(t))
        .getOrElse(t match
          case "city" | "town" | "settlement" | "village" | "temple" | "fort" | "sanctuary" | "cemetery" => Site
          case "country" | "area" | "province" | "landscape" | "valley" | "plain" | "island"             => Region
          case "state" | "kingdom" | "empire" | "polity" | "dynasty"                                     => Polity
          case "sea" | "lake" | "river" | "gulf" | "bay" | "strait"                                      => Water
          case _                                                                                         => Unknown)

    given WireEnumValues[KindHint]:
      def all: Array[KindHint] = KindHint.values
      def wire(e: KindHint): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[KindHint] =
        KindHint.values.find(_.wire == _root_.com.histmersion.api.catalogue.Geocode.KindHint.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
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

  def key(s: String): com.histmersion.api.catalogue.Geocode.Key =
    val b = new StringBuilder((s).length)
    Aside.replaceAllIn((s), " ").foreach { c =>
      if Apostrophes.contains(c) then ()
      else if Separators.contains(c) then b += ' '
      else LatinFold.foldChar((c)).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then _root_.com.histmersion.api.catalogue.Geocode.Key(spaced.drop(4)) else _root_.com.histmersion.api.catalogue.Geocode.Key(spaced)

  enum Approximation:
    case Qualifier(dropped: Approximation.Qualifier.Dropped)

    case OnlyWithin(missing: Approximation.OnlyWithin.Missing)

    case Rough(places: Approximation.Rough.Places)

    case GridSquare

    def describe: String = this match
      case Qualifier(d)  => (s"'$d' dropped")
      case OnlyWithin(m) => (s"'$m' not found there; the enclosing place's point")
      case Rough(n)      => (s"a placeholder point shared by $n places")
      case GridSquare    => ("the centre of an atlas grid square")

  object Approximation:
    object Qualifier:
      type Dropped = Dropped.T
      object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object OnlyWithin:
      type Missing = Missing.T
      object Missing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Rough:
      type Places = Places.T
      object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Attempt(name: Attempt.Name, within: Option[Attempt.Within] = None, approx: Option[Approximation] = None):
    def key: com.histmersion.api.catalogue.Geocode.Key = Geocode.key(name.value)

  object Attempt:

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Within = Within.T
    object Within extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  def stripQualifier(name: com.histmersion.api.catalogue.Geocode.Attempt.Name): Option[(com.histmersion.api.catalogue.Geocode.Approximation.Qualifier.Dropped, com.histmersion.api.catalogue.Geocode.Attempt.Name)] =
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

  def compassBox(dropped: com.histmersion.api.catalogue.Geocode.Approximation.Qualifier.Dropped): Option[(com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction)] =
    val words = dropped.value.split("[\\s\\-]+").map(w => key(w).value).filter(_.nonEmpty).toList
    val dirs = words.flatMap(w => Boxes.get(w.stripSuffix("ern")).orElse(Boxes.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  private def box(x0: Double, y0: Double, x1: Double, y1: Double) =
    (Compass.Fraction(x0), Compass.Fraction(y0), Compass.Fraction(x1), Compass.Fraction(y1))

  private val Boxes: Map[String, (com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction)] = Map(
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

  def attempts(name: com.histmersion.api.catalogue.Geocode.Attempt.Name): List[Attempt] =
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
      val all = List(Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(n))) ++ asides ++
        comma.map((a, in) => Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(a), Some(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Within(in)))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(in), None, Some(Approximation.OnlyWithin(_root_.com.histmersion.api.catalogue.Geocode.Approximation.OnlyWithin.Missing(a))))).toList
      all.distinctBy(a => (a.key, a.within.map(w => key(w.value))))

  final case class Candidate(
      id: com.histmersion.api.catalogue.Place.Id,
      label: com.histmersion.api.catalogue.Place.Label,
      kind: Candidate.Kind,
      byLabel: Candidate.ByLabel,
      from: Option[com.histmersion.api.catalogue.Year],
      to: Option[com.histmersion.api.catalogue.Year],
      dimension: Candidate.Dimension,
      area: Candidate.Area,
      shared: com.histmersion.api.catalogue.Geocode.Approximation.Rough.Places,
      lon: com.histmersion.api.catalogue.Longitude,
      lat: com.histmersion.api.catalogue.Latitude,
      tier: com.histmersion.api.catalogue.Gazetteer.Rank = _root_.com.histmersion.api.catalogue.Gazetteer.Rank(0),
      gridCell: Candidate.GridCell = _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(false)
  ):
    def undated: Boolean = (from.isEmpty && to.isEmpty)

    def span: com.histmersion.api.catalogue.Geocode.Candidate.Span = _root_.com.histmersion.api.catalogue.Geocode.Candidate.Span((for a <- from; b <- to yield (b).value - (a).value).getOrElse((_root_.com.histmersion.api.catalogue.Geocode.Candidate.Span(Long.MaxValue / 4)).value))
    def overlaps(p: Period): Boolean = from.forall(_.value <= p.end.value) && to.forall(_.value >= p.start.value)

    def gap(p: Period): com.histmersion.api.catalogue.Geocode.Candidate.Gap =
      if (overlaps(p)) then _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L)
      else
        to.filter(_.value < p.start.value)
          .map(t => _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(p.start.value - t.value))
          .orElse(from.filter(_.value > p.end.value).map(f => _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(f.value - p.end.value)))
          .getOrElse(_root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))

  object Candidate:
    type Gap = Gap.T
    object Gap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Span = Span.T
    object Span extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ByLabel = ByLabel.T
    object ByLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dimension = Dimension.T
    object Dimension extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Area = Area.T
    object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCell = GridCell.T
    object GridCell extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def broad(kind: com.histmersion.api.catalogue.Geocode.Candidate.Kind) =
    kind.value == com.histmersion.api.catalogue.Place.Kind.Region || kind.value == com.histmersion.api.catalogue.Place.Kind.Polity

  def tierOf(importSource: Option[com.histmersion.api.catalogue.Place.Match.ImportSource]): com.histmersion.api.catalogue.Gazetteer.Rank = Gazetteer.rank(Place.Match(_root_.com.histmersion.api.catalogue.Place.Id(""), importSource))

  def isPlaceholder(c: Candidate, placeholderMin: com.histmersion.api.catalogue.Geocode.Placeholder.Min): Boolean = c.dimension.value == 0 && c.shared.value >= placeholderMin.value

  private def kindRank(kind: com.histmersion.api.catalogue.Geocode.Candidate.Kind, hint: KindHint): com.histmersion.api.catalogue.Geocode.KindRank = hint match
    case KindHint.Site =>
      if kind.value == com.histmersion.api.catalogue.Place.Kind.Site then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0)
      else if kind.value == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)
    case KindHint.Water =>
      if kind.value == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0)
      else if kind.value == com.histmersion.api.catalogue.Place.Kind.Site then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)
    case _ => if broad(kind) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0) else if kind.value == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1) else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)

  private def timeRank(c: Candidate, at: Option[Period]): (com.histmersion.api.catalogue.Geocode.TimeRank.Class, com.histmersion.api.catalogue.Geocode.Candidate.Gap) = at match
    case None    => if ((c.undated) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(0), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L)) else (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(1), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(-(c.span).value))
    case Some(p) =>
      if ((c.undated) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(1), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))
      else if ((c.overlaps(p)) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(0), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))
      else (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(2), c.gap(p))

  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: com.histmersion.api.catalogue.Geocode.Placeholder.Min): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (
        if ((isPlaceholder(c, placeholderMin)) == (true)) || ((c.gridCell) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(true)) then 1 else 0,
        if ((c.byLabel) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.ByLabel(true)) then 0 else 1,
        c.tier.value,
        kindRank(c.kind, hint).value,
        if broad(c.kind) && c.dimension.value == 0 then 1 else 0,
        t.value,
        gap.value,
        c.area.value,
        c.id.value
      )
    }

  final case class Resolution(
      asked: Resolution.Asked,
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: com.histmersion.api.catalogue.Place.Label,
      lon: com.histmersion.api.catalogue.Longitude,
      lat: com.histmersion.api.catalogue.Latitude,
      approximate: List[Approximation] = Nil
  ):
    def isApproximate: Boolean = (approximate.nonEmpty)

    def describe: String =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      (s"'$asked' → $label [$placeId]$approx")

  object Resolution:

    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def choose(asked: com.histmersion.api.catalogue.Geocode.Resolution.Asked, attempt: Attempt, ranked: List[Candidate], placeholderMin: com.histmersion.api.catalogue.Geocode.Placeholder.Min): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(((isPlaceholder(c, placeholderMin)) == (true)))(Approximation.Rough(c.shared))
      val grid = Option.when(((c.gridCell) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(true)) && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  def distanceKm(lon1: com.histmersion.api.catalogue.Longitude, lat1: com.histmersion.api.catalogue.Latitude, lon2: com.histmersion.api.catalogue.Longitude, lat2: com.histmersion.api.catalogue.Latitude): com.histmersion.api.catalogue.Geocode.DistanceKm =
    val r = 6371.0
    val dLat = math.toRadians(((lat2).value - (lat1).value))
    val dLon = math.toRadians(((lon2).value - (lon1).value))
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians((lat1).value)) * math.cos(math.toRadians((lat2).value)) * math.pow(math.sin(dLon / 2), 2)
    _root_.com.histmersion.api.catalogue.Geocode.DistanceKm(2 * r * math.asin(math.min(1.0, math.sqrt(a))))

  final case class Move(
      findingId: com.histmersion.api.library.Finding.Id,
      citekey: com.histmersion.api.library.Citekey,
      asked: Move.Asked,
      region: Option[Move.Region],
      fromLon: Move.FromLon,
      fromLat: Move.FromLat,
      to: Resolution
  ):
    def km: com.histmersion.api.catalogue.Geocode.DistanceKm = distanceKm(fromLon, fromLat, to.lon, to.lat)

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
    object MinKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromLon = com.histmersion.api.catalogue.Longitude
    val FromLon: _root_.com.histmersion.api.catalogue.Longitude.type = _root_.com.histmersion.api.catalogue.Longitude
    type FromLat = com.histmersion.api.catalogue.Latitude
    val FromLat: _root_.com.histmersion.api.catalogue.Latitude.type = _root_.com.histmersion.api.catalogue.Latitude

  def worthMoving(m: Move, minKm: com.histmersion.api.catalogue.Geocode.Move.MinKm): Boolean = (m.km).value > minKm.value

  final case class Placement(findingId: com.histmersion.api.library.Finding.Id, citekey: com.histmersion.api.library.Citekey, region: Placement.Region, to: Resolution):
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
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def periodOf(from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(Year(math.min((a).value, (b).value)), Year(math.max((a).value, (b).value))))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
