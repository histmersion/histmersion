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

  type StripQualifierIsQW = StripQualifierIsQW.T
  object StripQualifierIsQW extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierGoResultPart1 = StripQualifierGoResultPart1.T
  object StripQualifierGoResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierGoResultPart2 = StripQualifierGoResultPart2.T
  object StripQualifierGoResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierGoRest = StripQualifierGoRest.T
  object StripQualifierGoRest extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierGoDropped = StripQualifierGoDropped.T
  object StripQualifierGoDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Separators = Separators.T
  object Separators extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Apostrophes = Apostrophes.T
  object Apostrophes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyS = KeyS.T
  object KeyS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QualifierWords = QualifierWords.T
  object QualifierWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Joiners = Joiners.T
  object Joiners extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierResultPart1 = StripQualifierResultPart1.T
  object StripQualifierResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierResultPart2 = StripQualifierResultPart2.T
  object StripQualifierResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripQualifierName = StripQualifierName.T
  object StripQualifierName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassBoxResultPart1 = CompassBoxResultPart1.T
  object CompassBoxResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassBoxResultPart2 = CompassBoxResultPart2.T
  object CompassBoxResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassBoxResultPart3 = CompassBoxResultPart3.T
  object CompassBoxResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassBoxResultPart4 = CompassBoxResultPart4.T
  object CompassBoxResultPart4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassBoxDropped = CompassBoxDropped.T
  object CompassBoxDropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassPart1 = CompassPart1.T
  object CompassPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassPart2Part1 = CompassPart2Part1.T
  object CompassPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassPart2Part2 = CompassPart2Part2.T
  object CompassPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassPart2Part3 = CompassPart2Part3.T
  object CompassPart2Part3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CompassPart2Part4 = CompassPart2Part4.T
  object CompassPart2Part4 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptsName = AttemptsName.T
  object AttemptsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BroadKind = BroadKind.T
  object BroadKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierOfResult = TierOfResult.T
  object TierOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierOfImportSource = TierOfImportSource.T
  object TierOfImportSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsPlaceholderResult = IsPlaceholderResult.T
  object IsPlaceholderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsPlaceholderPlaceholderMin = IsPlaceholderPlaceholderMin.T
  object IsPlaceholderPlaceholderMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindRankResult = KindRankResult.T
  object KindRankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindRankKind = KindRankKind.T
  object KindRankKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRankResultPart1 = TimeRankResultPart1.T
  object TimeRankResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeRankResultPart2 = TimeRankResultPart2.T
  object TimeRankResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RankPlaceholderMin = RankPlaceholderMin.T
  object RankPlaceholderMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChooseAsked = ChooseAsked.T
  object ChooseAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChoosePlaceholderMin = ChoosePlaceholderMin.T
  object ChoosePlaceholderMin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmResult = DistanceKmResult.T
  object DistanceKmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon1 = DistanceKmLon1.T
  object DistanceKmLon1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLat1 = DistanceKmLat1.T
  object DistanceKmLat1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLon2 = DistanceKmLon2.T
  object DistanceKmLon2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKmLat2 = DistanceKmLat2.T
  object DistanceKmLat2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorthMovingResult = WorthMovingResult.T
  object WorthMovingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorthMovingMinKm = WorthMovingMinKm.T
  object WorthMovingMinKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfFrom = PeriodOfFrom.T
  object PeriodOfFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodOfTo = PeriodOfTo.T
  object PeriodOfTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum KindHint:
    case Site, Region, Polity, Water, Unknown

    def wire: com.histmersion.api.catalogue.Geocode.KindHint.WireResult = _root_.com.histmersion.api.catalogue.Geocode.KindHint.WireResult(toString.toLowerCase(java.util.Locale.ROOT))

  object KindHint:
    /** Lenient: it parses a model's free answer, and an unrecognised word is no hint. */
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.catalogue.Geocode.KindHint.ParseS): KindHint =
      val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
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
      def wire(e: KindHint): com.histmersion.api.catalogue.Geocode.KindHint.WireResult = e.wire
      def fromWire(s: com.histmersion.api.catalogue.Geocode.KindHint.FromWireS): Option[KindHint] =
        KindHint.values.find(_.wire == (s).value.trim.toLowerCase(java.util.Locale.ROOT))
    given Encoder[KindHint] = WireEnum.encoder
    given Decoder[KindHint] = WireEnum.decoder
    given Schema[KindHint] = WireEnum.schema

  /** Must equal the separator list of the SQL function `histmersion_place_key`. */
  val Separators: Set[com.histmersion.api.catalogue.Geocode.Separators] =
    ((" \u00a0\t\n\r!\"#$%&()*+,-./:;<=>?@[\\]^_{|}~" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u201c\u201d\u201e\u00ab\u00bb\u00b7\u2026").toSet).map(refactorValue => _root_.com.histmersion.api.catalogue.Geocode.Separators(refactorValue))

  /** Apostrophe-likes, dropped ("Rus'" = "Rus"). Must equal the SQL function `histmersion_place_key`'s. */
  val Apostrophes: Set[com.histmersion.api.catalogue.Geocode.Apostrophes] = ("'`\u00b4\u2018\u2019\u02bc\u02b9".toSet).map(refactorValue2 => _root_.com.histmersion.api.catalogue.Geocode.Apostrophes(refactorValue2))

  private val Aside = "\\([^)]*\\)".r

  def key(s: com.histmersion.api.catalogue.Geocode.KeyS): com.histmersion.api.catalogue.Geocode.KeyResult =
    val b = new StringBuilder((s).value.length)
    Aside.replaceAllIn((s).value, " ").foreach { c =>
      if Apostrophes.contains(_root_.com.histmersion.api.catalogue.Geocode.Apostrophes(c)) then ()
      else if Separators.contains(_root_.com.histmersion.api.catalogue.Geocode.Separators(c)) then b += ' '
      else LatinFold.foldChar(c).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then _root_.com.histmersion.api.catalogue.Geocode.KeyResult(spaced.drop(4)) else _root_.com.histmersion.api.catalogue.Geocode.KeyResult(spaced)

  enum Approximation:
    case Qualifier(dropped: Approximation.Qualifier.Dropped)

    case OnlyWithin(missing: Approximation.OnlyWithin.Missing)

    case Rough(places: Approximation.Rough.Places)

    case GridSquare

    def describe: com.histmersion.api.catalogue.Geocode.Approximation.DescribeResult = this match
      case Qualifier(d)  => _root_.com.histmersion.api.catalogue.Geocode.Approximation.DescribeResult(s"'$d' dropped")
      case OnlyWithin(m) => _root_.com.histmersion.api.catalogue.Geocode.Approximation.DescribeResult(s"'$m' not found there; the enclosing place's point")
      case Rough(n)      => _root_.com.histmersion.api.catalogue.Geocode.Approximation.DescribeResult(s"a placeholder point shared by $n places")
      case GridSquare    => _root_.com.histmersion.api.catalogue.Geocode.Approximation.DescribeResult("the centre of an atlas grid square")

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

    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Attempt(name: Attempt.Name, within: Option[Attempt.Within] = None, approx: Option[Approximation] = None):
    def key: com.histmersion.api.catalogue.Geocode.Attempt.KeyResult = Geocode.key(name)

  object Attempt:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Within = Within.T
    object Within extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val QualifierWords: Set[com.histmersion.api.catalogue.Geocode.QualifierWords] = Set(
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("north"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("south"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("east"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("west"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("northern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("southern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("eastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("western"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("northeast"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("northwest"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("southeast"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("southwest"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("northeastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("northwestern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("southeastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("southwestern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("central"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("upper"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("lower"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("inner"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("outer"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("greater"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("coastal"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("interior"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("middle"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("mid"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("highland"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWords("lowland")
  )

  private val Joiners: Set[com.histmersion.api.catalogue.Geocode.Joiners] = Set(_root_.com.histmersion.api.catalogue.Geocode.Joiners("and"), _root_.com.histmersion.api.catalogue.Geocode.Joiners("or"))

  def stripQualifier(name: com.histmersion.api.catalogue.Geocode.StripQualifierName): Option[(com.histmersion.api.catalogue.Geocode.StripQualifierResultPart1, com.histmersion.api.catalogue.Geocode.StripQualifierResultPart2)] =
    val words = (name).value.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: com.histmersion.api.catalogue.Geocode.StripQualifierIsQW) = QualifierWords.contains(key(w))
    @annotation.tailrec
    def go(rest: List[com.histmersion.api.catalogue.Geocode.StripQualifierGoRest], dropped: List[com.histmersion.api.catalogue.Geocode.StripQualifierGoDropped]): (List[com.histmersion.api.catalogue.Geocode.StripQualifierGoResultPart1], List[com.histmersion.api.catalogue.Geocode.StripQualifierGoResultPart2]) = rest match
      case w :: tail if isQ(w)                                                      => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j)) && isQ(w) => go(tail, dropped :+ j :+ w)
      case _                                                                        => (dropped, rest)
    val (dropped, rest) = go((words).map(refactorValue6 => _root_.com.histmersion.api.catalogue.Geocode.StripQualifierGoRest(refactorValue6)), Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && key(rest.mkString(" ")).nonEmpty)(
      (_root_.com.histmersion.api.catalogue.Geocode.StripQualifierResultPart1(dropped.mkString(" ")), _root_.com.histmersion.api.catalogue.Geocode.StripQualifierResultPart2(rest.mkString(" ")))
    )

  def compassBox(dropped: com.histmersion.api.catalogue.Geocode.CompassBoxDropped): Option[(com.histmersion.api.catalogue.Geocode.CompassBoxResultPart1, com.histmersion.api.catalogue.Geocode.CompassBoxResultPart2, com.histmersion.api.catalogue.Geocode.CompassBoxResultPart3, com.histmersion.api.catalogue.Geocode.CompassBoxResultPart4)] =
    val words = (dropped).value.split("[\\s\\-]+").map(key).filter(_.nonEmpty).toList
    val dirs = words.flatMap(w => Compass.get(w.stripSuffix("ern")).orElse(Compass.get(w)))
    dirs match
      case List(box) => Some(box)
      case _         => None

  private val Compass: Map[com.histmersion.api.catalogue.Geocode.CompassPart1, (com.histmersion.api.catalogue.Geocode.CompassPart2Part1, com.histmersion.api.catalogue.Geocode.CompassPart2Part2, com.histmersion.api.catalogue.Geocode.CompassPart2Part3, com.histmersion.api.catalogue.Geocode.CompassPart2Part4)] = Map(
    { val refactorTuple = "north" -> (0.0, 0.5, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple._1), { val refactorTuple2 = refactorTuple._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple2._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple2._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple2._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple2._4)) }) },
    { val refactorTuple3 = "south" -> (0.0, 0.0, 1.0, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple3._1), { val refactorTuple4 = refactorTuple3._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple4._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple4._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple4._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple4._4)) }) },
    { val refactorTuple5 = "east" -> (0.5, 0.0, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple5._1), { val refactorTuple6 = refactorTuple5._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple6._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple6._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple6._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple6._4)) }) },
    { val refactorTuple7 = "west" -> (0.0, 0.0, 0.5, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple7._1), { val refactorTuple8 = refactorTuple7._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple8._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple8._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple8._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple8._4)) }) },
    { val refactorTuple31 = "northeast" -> (0.5, 0.5, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple31._1), { val refactorTuple32 = refactorTuple31._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple32._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple32._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple32._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple32._4)) }) },
    { val refactorTuple33 = "northwest" -> (0.0, 0.5, 0.5, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple33._1), { val refactorTuple34 = refactorTuple33._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple34._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple34._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple34._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple34._4)) }) },
    { val refactorTuple13 = "southeast" -> (0.5, 0.0, 1.0, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple13._1), { val refactorTuple14 = refactorTuple13._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple14._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple14._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple14._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple14._4)) }) },
    { val refactorTuple15 = "southwest" -> (0.0, 0.0, 0.5, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple15._1), { val refactorTuple16 = refactorTuple15._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple16._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple16._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple16._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple16._4)) }) },
    { val refactorTuple17 = "central" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple17._1), { val refactorTuple18 = refactorTuple17._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple18._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple18._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple18._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple18._4)) }) },
    { val refactorTuple41 = "mid" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple41._1), { val refactorTuple42 = refactorTuple41._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple42._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple42._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple42._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple42._4)) }) },
    { val refactorTuple43 = "middle" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.CompassPart1(refactorTuple43._1), { val refactorTuple44 = refactorTuple43._2; (_root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part1(refactorTuple44._1), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part2(refactorTuple44._2), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part3(refactorTuple44._3), _root_.com.histmersion.api.catalogue.Geocode.CompassPart2Part4(refactorTuple44._4)) }) }
  )

  private val Asides = "\\(([^)]*)\\)".r

  def attempts(name: com.histmersion.api.catalogue.Geocode.AttemptsName): List[Attempt] =
    val n = (name).value.trim
    if (key(n)).value.isEmpty then Nil
    else
      val asides = Asides.findAllMatchIn(n).map(_.group(1).trim).filter(a => key(a).nonEmpty).map(Attempt(_)).toList
      val comma = n.lastIndexOf(',') match
        case i if i > 0 && key(n.take(i)).nonEmpty && key(n.drop(i + 1)).nonEmpty =>
          Some((n.take(i).trim, n.drop(i + 1).trim))
        case _ => None
      val head = comma.fold(n)(_._1)
      val qualified =
        stripQualifier(head).map((q, base) => Attempt(base, comma.map(_._2), Some(Approximation.Qualifier(q))))
      val all = List(Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(n))) ++ asides ++
        comma.map((a, in) => Attempt(a, Some(in))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(in, None, Some(Approximation.OnlyWithin(a)))).toList
      all.distinctBy(a => (a.key, a.within.map(key)))

  final case class Candidate(
      id: Candidate.Id,
      label: Candidate.Label,
      kind: Candidate.Kind,
      byLabel: Candidate.ByLabel,
      from: Option[Candidate.From],
      to: Option[Candidate.To],
      dimension: Candidate.Dimension,
      area: Candidate.Area,
      shared: Candidate.Shared,
      lon: com.histmersion.api.catalogue.Longitude,
      lat: com.histmersion.api.catalogue.Latitude,
      tier: Candidate.Tier = _root_.com.histmersion.api.catalogue.Geocode.Candidate.Tier(0),
      gridCell: Candidate.GridCell = _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(false)
  ):
    def undated: com.histmersion.api.catalogue.Geocode.Candidate.UndatedResult = _root_.com.histmersion.api.catalogue.Geocode.Candidate.UndatedResult(from.isEmpty && to.isEmpty)

    def span: com.histmersion.api.catalogue.Geocode.Candidate.SpanResult = (for a <- from; b <- to yield (b).value - a).getOrElse((_root_.com.histmersion.api.catalogue.Geocode.Candidate.SpanResult(Long.MaxValue / 4)).value)
    def overlaps(p: Period): com.histmersion.api.catalogue.Geocode.Candidate.OverlapsResult = from.forall(_ <= p.end) && to.forall(_ >= p.start)

    def gap(p: Period): com.histmersion.api.catalogue.Geocode.Candidate.GapResult =
      if (overlaps(p)).value then _root_.com.histmersion.api.catalogue.Geocode.Candidate.GapResult(0L)
      else to.filter((_).value < p.start).map(p.start - _).orElse(from.filter((_).value > p.end).map((_).value - p.end)).getOrElse(_root_.com.histmersion.api.catalogue.Geocode.Candidate.GapResult(0L))

  object Candidate:
    type UndatedResult = UndatedResult.T
    object UndatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SpanResult = SpanResult.T
    object SpanResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OverlapsResult = OverlapsResult.T
    object OverlapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GapResult = GapResult.T
    object GapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ByLabel = ByLabel.T
    object ByLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Dimension = Dimension.T
    object Dimension extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Area = Area.T
    object Area extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Shared = Shared.T
    object Shared extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tier = Tier.T
    object Tier extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCell = GridCell.T
    object GridCell extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def broad(kind: com.histmersion.api.catalogue.Geocode.BroadKind) =
    kind == com.histmersion.api.catalogue.Place.Kind.Region || kind == com.histmersion.api.catalogue.Place.Kind.Polity

  def tierOf(importSource: Option[com.histmersion.api.catalogue.Geocode.TierOfImportSource]): com.histmersion.api.catalogue.Geocode.TierOfResult = Gazetteer.rank(Place.Match("", importSource))

  def isPlaceholder(c: Candidate, placeholderMin: com.histmersion.api.catalogue.Geocode.IsPlaceholderPlaceholderMin): com.histmersion.api.catalogue.Geocode.IsPlaceholderResult = c.dimension == 0 && c.shared >= placeholderMin

  private def kindRank(kind: com.histmersion.api.catalogue.Geocode.KindRankKind, hint: KindHint): com.histmersion.api.catalogue.Geocode.KindRankResult = hint match
    case KindHint.Site =>
      if kind == com.histmersion.api.catalogue.Place.Kind.Site then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(0)
      else if kind == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(2)
    case KindHint.Water =>
      if kind == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(0)
      else if kind == com.histmersion.api.catalogue.Place.Kind.Site then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(2)
    case _ => if broad(kind) then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(0) else if kind == com.histmersion.api.catalogue.Place.Kind.Feature then _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(1) else _root_.com.histmersion.api.catalogue.Geocode.KindRankResult(2)

  private def timeRank(c: Candidate, at: Option[Period]): (com.histmersion.api.catalogue.Geocode.TimeRankResultPart1, com.histmersion.api.catalogue.Geocode.TimeRankResultPart2) = at match
    case None    => if c.undated then (_root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart1(0), _root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart2(0L)) else (_root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart1(1), -c.span)
    case Some(p) =>
      if c.undated then (_root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart1(1), _root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart2(0L))
      else if c.overlaps(p) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart1(0), _root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart2(0L))
      else (_root_.com.histmersion.api.catalogue.Geocode.TimeRankResultPart1(2), c.gap(p))

  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: com.histmersion.api.catalogue.Geocode.RankPlaceholderMin): List[Candidate] =
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
      asked: Resolution.Asked,
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: Resolution.Label,
      lon: com.histmersion.api.catalogue.Longitude,
      lat: com.histmersion.api.catalogue.Latitude,
      approximate: List[Approximation] = Nil
  ):
    def isApproximate: com.histmersion.api.catalogue.Geocode.Resolution.IsApproximateResult = _root_.com.histmersion.api.catalogue.Geocode.Resolution.IsApproximateResult(approximate.nonEmpty)

    def describe: com.histmersion.api.catalogue.Geocode.Resolution.DescribeResult =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      _root_.com.histmersion.api.catalogue.Geocode.Resolution.DescribeResult(s"'$asked' → $label [$placeId]$approx")

  object Resolution:
    type IsApproximateResult = IsApproximateResult.T
    object IsApproximateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def choose(asked: com.histmersion.api.catalogue.Geocode.ChooseAsked, attempt: Attempt, ranked: List[Candidate], placeholderMin: com.histmersion.api.catalogue.Geocode.ChoosePlaceholderMin): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(isPlaceholder(c, placeholderMin))(Approximation.Rough(c.shared))
      val grid = Option.when(c.gridCell && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  def distanceKm(lon1: com.histmersion.api.catalogue.Geocode.DistanceKmLon1, lat1: com.histmersion.api.catalogue.Geocode.DistanceKmLat1, lon2: com.histmersion.api.catalogue.Geocode.DistanceKmLon2, lat2: com.histmersion.api.catalogue.Geocode.DistanceKmLat2): com.histmersion.api.catalogue.Geocode.DistanceKmResult =
    val r = 6371.0
    val dLat = math.toRadians(((lat2).value - lat1).value)
    val dLon = math.toRadians(((lon2).value - lon1).value)
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians(lat1)) * math.cos(math.toRadians(lat2)) * math.pow(math.sin(dLon / 2), 2)
    _root_.com.histmersion.api.catalogue.Geocode.DistanceKmResult(2 * r * math.asin(math.min(1.0, math.sqrt(a))))

  final case class Move(
      findingId: com.histmersion.api.library.Finding.Id,
      citekey: com.histmersion.api.library.Citekey,
      asked: Move.Asked,
      region: Option[Move.Region],
      fromLon: Move.FromLon,
      fromLat: Move.FromLat,
      to: Resolution
  ):
    def km: com.histmersion.api.catalogue.Geocode.Move.KmResult = distanceKm(fromLon, fromLat, to.lon, to.lat)

    def describe: com.histmersion.api.catalogue.Geocode.Move.DescribeResult =
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

  object Move:
    type KmResult = KmResult.T
    object KmResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromLon = FromLon.T
    object FromLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromLat = FromLat.T
    object FromLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def worthMoving(m: Move, minKm: com.histmersion.api.catalogue.Geocode.WorthMovingMinKm): com.histmersion.api.catalogue.Geocode.WorthMovingResult = m.km > minKm

  final case class Placement(findingId: com.histmersion.api.library.Finding.Id, citekey: com.histmersion.api.library.Citekey, region: Placement.Region, to: Resolution):
    def describe: com.histmersion.api.catalogue.Geocode.Placement.DescribeResult =
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

  object Placement:
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def periodOf(from: Option[com.histmersion.api.catalogue.Geocode.PeriodOfFrom], to: Option[com.histmersion.api.catalogue.Geocode.PeriodOfTo]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(math.min((a).value, (b).value), math.max((a).value, (b).value)))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
