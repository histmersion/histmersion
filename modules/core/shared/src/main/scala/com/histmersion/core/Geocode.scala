package com.histmersion.core

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/** Place NAME → gazetteer point: the pure half of the geocoder (#981). `db.Geocoder` fetches the
  * candidate places for a name; everything that decides WHICH of them a person would mean lives here.
  *
  * A name is tried as a sequence of [[Attempt]]s, the first that finds a place wins:
  *
  *   1. the name itself, compared by [[key]] (diacritics, case, apostrophes, punctuation, a
  *      parenthesised aside and a leading "the" do not matter: "Kraków" = "Krakow", "Ur" = "Ur(i)");
  *   2. a parenthesised aside on its own ("Ur (Tell el-Muqayyar)" → "Tell el-Muqayyar");
  *   3. "X, Y" — X among the places inside Y ("Kormadin, Serbia");
  *   4. a directional or qualifying prefix dropped ("northern Levant" → "Levant") — APPROXIMATE; one
  *      compass word moves the point into that part of the shape ([[compassBox]], #987);
  *   5. for "X, Y", Y alone when X is not found inside it — APPROXIMATE.
  *
  * Among the candidates of one attempt, [[rank]] orders by, in this order:
  *
  *   1. a real point before a PLACEHOLDER one: a point shared by `placeholderMin` or more places is
  *      a grid-snapped stand-in (Pleiades files 17 places, three of them "Mesopotamia", at 37.5 N
  *      37.5 E), and so is any Pleiades point on the centre of an atlas grid square
  *      ([[LocationPrecision.GridCell]] — Thessalia the province at 37.5 N 22.5 E, in the
  *      Peloponnese); both rank below every precise candidate, and a choice that lands on one is
  *      marked approximate;
  *   2. the place's own LABEL before one of its alternative names — "Albania" means the country
  *      labelled Albania, not the Iranian site that lists "Albania" among five names;
  *   2a. who brought the place in ([[tierOf]], #1064): the bundled seed (Pleiades, Natural Earth, the
  *      curated supplement) and a person's own places before a scholarly LPF dataset, before
  *      Wikidata, before GeoNames — the bulk sources fill the gaps the curated ones leave and never
  *      outrank them ("Varna" stays the city, not GeoNames' province point; "Ur" stays Ur(i));
  *   3. the kind the finding asked for ([[KindHint]]): a site for a site, a body of water for water;
  *      without a hint a BROAD entity (region, polity) before a feature before a site;
  *   4. a broad entity with a real shape before one that is a bare point;
  *   5. TIME, when the finding is dated: a dated place whose period overlaps the finding's, then an
  *      undated (always valid) place, then the dated ones that do not overlap, nearest first — so
  *      "Kievan Rus'" in 1000 CE is the 990–1017 snapshot, not the 1236–1240 one. An undated
  *      finding prefers an undated place, then the longest-lived dated one. A place matched by a
  *      DATED name is dated by that name too (`from`/`to` are the place's period narrowed by the
  *      name's), so a historical name wins for a finding of its own time (#1064);
  *   6. the smaller area (the more specific shape), then the id (a stable tie-break).
  *
  * Nothing is invented: a name no attempt finds is unknown, and the caller says so. */
object Geocode:

  /** What kind of place the finding is about, as the model judged it. `Unknown` = no preference. */
  enum KindHint:
    case Site, Region, Polity, Water, Unknown

    def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

  object KindHint:
    /** Lenient on purpose: it parses a model's free answer, and an unrecognised word is no hint. */
    def parse(s: String): KindHint =
      val t = s.trim.toLowerCase(java.util.Locale.ROOT)
      values.find(_.wire == t).getOrElse(t match
        case "city" | "town" | "settlement" | "village" | "temple" | "fort" | "sanctuary" | "cemetery" => Site
        case "country" | "area" | "province" | "landscape" | "valley" | "plain" | "island"            => Region
        case "state" | "kingdom" | "empire" | "polity" | "dynasty"                                     => Polity
        case "sea" | "lake" | "river" | "gulf" | "bay" | "strait"                                      => Water
        case _                                                                                         => Unknown)

    given WireEnumValues[KindHint]:
      def all: Array[KindHint] = KindHint.values
      def wire(e: KindHint): String = e.wire
      def fromWire(s: String): Option[KindHint] = KindHint.values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
    given Encoder[KindHint] = WireEnum.encoder
    given Decoder[KindHint] = WireEnum.decoder
    given Schema[KindHint]  = WireEnum.schema

  // ── the key ────────────────────────────────────────────────────────────────────────────────────

  /** Characters that separate words. MUST equal the separator list of `histmersion_place_key` (V067). */
  val Separators: Set[Char] =
    (" \u00a0\t\n\r!\"#$%&()*+,-./:;<=>?@[\\]^_{|}~" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u201c\u201d\u201e\u00ab\u00bb\u00b7\u2026").toSet

  /** Apostrophe-likes, dropped without a trace ("Rus'" = "Rus"). MUST equal V067's. */
  val Apostrophes: Set[Char] = "'`\u00b4\u2018\u2019\u02bc\u02b9".toSet

  private val Aside = "\\([^)]*\\)".r

  /** The normal form two place names are compared by — the Scala twin of the SQL function
    * `histmersion_place_key` (V067), which `GeocoderSuite` holds to it character by character. */
  def key(s: String): String =
    val b = new StringBuilder(s.length) // local accumulator
    Aside.replaceAllIn(s, " ").foreach { c =>
      if Apostrophes.contains(c) then ()
      else if Separators.contains(c) then b += ' '
      else LatinFold.foldChar(c).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then spaced.drop(4) else spaced

  // ── attempts ───────────────────────────────────────────────────────────────────────────────────

  /** Why a choice is only near what was asked. */
  enum Approximation:
    /** "northern Albania" → Albania: the qualifier was dropped. */
    case Qualifier(dropped: String)
    /** "Gornij, Croatia" → Croatia: the place itself is unknown, the area it was said to be in is not. */
    case OnlyWithin(missing: String)
    /** The point is a placeholder shared by `places` gazetteer entries. */
    case Rough(places: Int)
    /** The point is only the centre of the atlas grid square the place lies in. */
    case GridSquare

    def describe: String = this match
      case Qualifier(d)   => s"'$d' dropped"
      case OnlyWithin(m)  => s"'$m' not found there; the enclosing place's point"
      case Rough(n)       => s"a placeholder point shared by $n places"
      case GridSquare     => "the centre of an atlas grid square"

  /** One way to read a name. `within` is a further name the place must lie inside. */
  final case class Attempt(name: String, within: Option[String] = None, approx: Option[Approximation] = None):
    def key: String = Geocode.key(name)

  private val QualifierWords: Set[String] = Set(
    "north", "south", "east", "west", "northern", "southern", "eastern", "western",
    "northeast", "northwest", "southeast", "southwest", "northeastern", "northwestern", "southeastern",
    "southwestern", "central", "upper", "lower", "inner", "outer", "greater", "coastal", "interior",
    "middle", "mid", "highland", "lowland")

  private val Joiners: Set[String] = Set("and", "or")

  /** `"southern Levant"` → `Some(("southern", "Levant"))`; `"Western and Central Balkans"` →
    * `Some(("Western and Central", "Balkans"))`. Only whole words, and something must remain. */
  def stripQualifier(name: String): Option[(String, String)] =
    val words = name.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: String)   = QualifierWords.contains(key(w))
    // qualifiers, optionally joined by and/or, then the rest
    @annotation.tailrec
    def go(rest: List[String], dropped: List[String]): (List[String], List[String]) = rest match
      case w :: tail if isQ(w)                                                        => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j)) && isQ(w)   => go(tail, dropped :+ j :+ w)
      case _                                                                          => (dropped, rest)
    val (dropped, rest) = go(words, Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && key(rest.mkString(" ")).nonEmpty)(
      (dropped.mkString(" "), rest.mkString(" ")))

  /** The part of a shape's bounding box a dropped qualifier points at, as fractions of the box
    * (`x0, y0, x1, y1`, 0 = west/south edge, 1 = east/north edge): "Eastern Mediterranean" is the
    * eastern half of the Mediterranean, not its point on the surface, which lies 2 000 km west (#987).
    * Only ONE direction is honoured — "Western and Central Balkans" names two, and keeps the whole
    * shape; a non-compass qualifier ("upper", "coastal") keeps it too. Pure. */
  def compassBox(dropped: String): Option[(Double, Double, Double, Double)] =
    val words = dropped.split("[\\s\\-]+").map(key).filter(_.nonEmpty).toList
    val dirs  = words.flatMap(w => Compass.get(w.stripSuffix("ern")).orElse(Compass.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  private val Compass: Map[String, (Double, Double, Double, Double)] = Map(
    "north" -> (0.0, 0.5, 1.0, 1.0), "south" -> (0.0, 0.0, 1.0, 0.5),
    "east" -> (0.5, 0.0, 1.0, 1.0), "west" -> (0.0, 0.0, 0.5, 1.0),
    "northeast" -> (0.5, 0.5, 1.0, 1.0), "northwest" -> (0.0, 0.5, 0.5, 1.0),
    "southeast" -> (0.5, 0.0, 1.0, 0.5), "southwest" -> (0.0, 0.0, 0.5, 0.5),
    "central" -> (0.25, 0.25, 0.75, 0.75), "mid" -> (0.25, 0.25, 0.75, 0.75), "middle" -> (0.25, 0.25, 0.75, 0.75))

  private val Asides = "\\(([^)]*)\\)".r

  /** The readings of `name`, in the order they are tried. Pure; duplicates (by key) removed. */
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
      val qualified = stripQualifier(head).map((q, base) =>
        Attempt(base, comma.map(_._2), Some(Approximation.Qualifier(q))))
      val all = List(Attempt(n)) ++ asides ++
        comma.map((a, in) => Attempt(a, Some(in))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(in, None, Some(Approximation.OnlyWithin(a)))).toList
      all.distinctBy(a => (a.key, a.within.map(key)))

  // ── ranking ────────────────────────────────────────────────────────────────────────────────────

  /** One gazetteer place a name matched. `from`/`to` are the inclusive bounds of its period (`None` =
    * unbounded on that side; both `None` = undated, always valid). `dimension` is the geometry's
    * (0 point, 1 line, 2 area); `shared` is how many places sit on exactly this point (1 for a shape);
    * `lon`/`lat` is the point the finding gets (on the surface of a shape); `gridCell` says that point
  * is only the centre of an atlas grid square ([[LocationPrecision]]). */
  final case class Candidate(
      id: String, label: String, kind: String, byLabel: Boolean,
      from: Option[Long], to: Option[Long], dimension: Int, area: Double, shared: Int,
      lon: Double, lat: Double, tier: Int = 0, gridCell: Boolean = false):
    def undated: Boolean = from.isEmpty && to.isEmpty
    /** How many years the place's period lasts; an open end counts as very long. */
    def span: Long = (for a <- from; b <- to yield b - a).getOrElse(Long.MaxValue / 4)
    def overlaps(p: Period): Boolean = from.forall(_ <= p.end) && to.forall(_ >= p.start)
    /** Years between the place's period and `p` (0 when they overlap). */
    def gap(p: Period): Long =
      if overlaps(p) then 0L
      else to.filter(_ < p.start).map(p.start - _).orElse(from.filter(_ > p.end).map(_ - p.end)).getOrElse(0L)

  private def broad(kind: String) = kind == PlaceKind.Region || kind == PlaceKind.Polity

  /** Who brought a place in, best first: 0 the bundled seed or a person's own (`import_source` NULL),
    * 1 an LPF dataset, 2 Wikidata, 3 GeoNames — [[Gazetteer.rank]]'s order, which also decides which
    * place a record joins. `Geocoder.tierSql` is its SQL twin. */
  def tierOf(importSource: Option[String]): Int = Gazetteer.rank(PlaceMatch("", importSource))

  def isPlaceholder(c: Candidate, placeholderMin: Int): Boolean = c.dimension == 0 && c.shared >= placeholderMin

  private def kindRank(kind: String, hint: KindHint): Int = hint match
    case KindHint.Site  => if kind == PlaceKind.Site then 0 else if kind == PlaceKind.Feature then 1 else 2
    case KindHint.Water => if kind == PlaceKind.Feature then 0 else if kind == PlaceKind.Site then 1 else 2
    case _              => if broad(kind) then 0 else if kind == PlaceKind.Feature then 1 else 2

  /** (rank, then smaller first): an undated finding prefers an undated place, then the LONGEST-lived
    * dated one (the 1040–1235 Kievan Rus', not its 1236–1240 rump at Kerch). */
  private def timeRank(c: Candidate, at: Option[Period]): (Int, Long) = at match
    case None    => if c.undated then (0, 0L) else (1, -c.span)
    case Some(p) =>
      if c.undated then (1, 0L)
      else if c.overlaps(p) then (0, 0L)
      else (2, c.gap(p))

  /** The candidates best first (the rules are in the object's doc). */
  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: Int): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (if isPlaceholder(c, placeholderMin) || c.gridCell then 1 else 0,
        if c.byLabel then 0 else 1,
        c.tier,
        kindRank(c.kind, hint),
        if broad(c.kind) && c.dimension == 0 then 1 else 0,
        t, gap, c.area, c.id)
    }

  /** What a name resolved to: the place, its point, and why the point is only approximate (empty =
    * the place that was asked for). `asked` is the name as given. */
  final case class Resolution(asked: String, placeId: String, label: String, lon: Double, lat: Double,
      approximate: List[Approximation] = Nil):
    def isApproximate: Boolean = approximate.nonEmpty
    /** One line for a run trace or a lint item. */
    def describe: String =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      s"'$asked' → $label [$placeId]$approx"

  /** The choice for one attempt's ranked candidates. */
  def choose(asked: String, attempt: Attempt, ranked: List[Candidate], placeholderMin: Int): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(isPlaceholder(c, placeholderMin))(Approximation.Rough(c.shared))
      val grid  = Option.when(c.gridCell && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  /** Great-circle distance in km (haversine on a 6371 km sphere) — how far a re-geocoded point moves. */
  def distanceKm(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double =
    val r    = 6371.0
    val dLat = math.toRadians(lat2 - lat1)
    val dLon = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) * math.pow(math.sin(dLon / 2), 2)
    2 * r * math.asin(math.min(1.0, math.sqrt(a)))

  // ── the repair (`histmersion lint --rule finding-geocode`) ─────────────────────────────────────────

  /** A finding whose stored point is the one the pre-#981 geocoder chose for `asked`, and where the
    * current ranking puts it. `region` is what the finding stores today (the old pipeline wrote the
    * chosen place's LABEL there, e.g. "[Hulwan]/Valashfarr/Beth Lashpar/Chala/Albania"). */
  final case class Move(findingId: String, citekey: String, asked: String, region: Option[String],
      fromLon: Double, fromLat: Double, to: Resolution):
    def km: Double = distanceKm(fromLon, fromLat, to.lon, to.lat)

    /** One lint line: what moves, from where, to what, and how far. */
    def describe: String =
      val from = region.filter(_ != asked).fold("")(r => s" (stored as '$r')")
      String.format(java.util.Locale.ROOT, "%s: '%s'%s at %.2f N %.2f E → %s at %.2f N %.2f E, %.0f km%s",
        findingId, asked, from, fromLat, fromLon, to.label + " [" + to.placeId + "]", to.lat, to.lon, km,
        if to.isApproximate then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else "")

  /** Only a move of more than `minKm` is worth making — a few km is the same place drawn differently. */
  def worthMoving(m: Move, minKm: Double): Boolean = m.km > minKm

  // ── the fill (`histmersion lint --rule finding-unplaced`, #987) ──────────────────────────────────

  /** A finding that names a place (`region`) but has NO point, and where the gazetteer now puts that
    * name — after a seed added the English names ("Dead Sea", "Balkans", "Kraków") the old one lacked.
    * `region` is the stored name, kept as it is: the fill adds a point, never renames. */
  final case class Placement(findingId: String, citekey: String, region: String, to: Resolution):
    /** One lint line: which finding, the name, where it lands. */
    def describe: String =
      String.format(java.util.Locale.ROOT, "%s: '%s' → %s at %.2f N %.2f E%s",
        findingId, region, to.label + " [" + to.placeId + "]", to.lat, to.lon,
        if to.isApproximate then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else "")

  /** The period a geocoder ranks a stored finding by: its own, or one year when only one end is known. */
  def periodOf(from: Option[Long], to: Option[Long]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(math.min(a, b), math.max(a, b)))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
