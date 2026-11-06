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
    type Direction = Direction.T
    object Direction extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fraction = Fraction.T
    object Fraction extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Apostrophe = Apostrophe.T
  object Apostrophe extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Joiner = Joiner.T
  object Joiner extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Key = Key.T
  object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindRank = KindRank.T
  object KindRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Latitude = Latitude.T
  object Latitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Longitude = Longitude.T
  object Longitude extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QualifierWord = QualifierWord.T
  object QualifierWord extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Separator = Separator.T
  object Separator extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Separators = SeparatorsValue
  type SeparatorsValue = com.histmersion.api.catalogue.Geocode.Separator
  val SeparatorsValue: _root_.com.histmersion.api.catalogue.Geocode.Separator.type = _root_.com.histmersion.api.catalogue.Geocode.Separator
  type Apostrophes = ApostrophesValue
  type ApostrophesValue = com.histmersion.api.catalogue.Geocode.Apostrophe
  val ApostrophesValue: _root_.com.histmersion.api.catalogue.Geocode.Apostrophe.type = _root_.com.histmersion.api.catalogue.Geocode.Apostrophe
  type QualifierWords = QualifierWordsValue
  type QualifierWordsValue = com.histmersion.api.catalogue.Geocode.QualifierWord
  val QualifierWordsValue: _root_.com.histmersion.api.catalogue.Geocode.QualifierWord.type = _root_.com.histmersion.api.catalogue.Geocode.QualifierWord
  type Joiners = JoinersValue
  type JoinersValue = com.histmersion.api.catalogue.Geocode.Joiner
  val JoinersValue: _root_.com.histmersion.api.catalogue.Geocode.Joiner.type = _root_.com.histmersion.api.catalogue.Geocode.Joiner
  type BroadKind = _root_.com.histmersion.api.catalogue.Geocode.Candidate.Kind
  val BroadKind: _root_.com.histmersion.api.catalogue.Geocode.Candidate.Kind.type = _root_.com.histmersion.api.catalogue.Geocode.Candidate.Kind
  type TierOfImportSource = _root_.com.histmersion.api.catalogue.Place.Match.ImportSource
  val TierOfImportSource: _root_.com.histmersion.api.catalogue.Place.Match.ImportSource.type = _root_.com.histmersion.api.catalogue.Place.Match.ImportSource
  type KindRankKind = _root_.com.histmersion.api.catalogue.Geocode.BroadKind
  val KindRankKind: _root_.com.histmersion.api.catalogue.Geocode.BroadKind.type = _root_.com.histmersion.api.catalogue.Geocode.BroadKind
  type RankPlaceholderMin = _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min
  val RankPlaceholderMin: _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min.type = _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min
  type ChooseAsked = _root_.com.histmersion.api.catalogue.Geocode.Resolution.Asked
  val ChooseAsked: _root_.com.histmersion.api.catalogue.Geocode.Resolution.Asked.type = _root_.com.histmersion.api.catalogue.Geocode.Resolution.Asked
  type ChoosePlaceholderMin = _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min
  val ChoosePlaceholderMin: _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min.type = _root_.com.histmersion.api.catalogue.Geocode.Placeholder.Min

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
  val Separators: Set[com.histmersion.api.catalogue.Geocode.Separators] =
    ((" \u00a0\t\n\r!\"#$%&()*+,-./:;<=>?@[\\]^_{|}~" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u201c\u201d\u201e\u00ab\u00bb\u00b7\u2026").toSet).map(refactorValue => _root_.com.histmersion.api.catalogue.Geocode.Separator(refactorValue))

  /** Apostrophe-likes, dropped ("Rus'" = "Rus"). Must equal the SQL function `histmersion_place_key`'s. */
  val Apostrophes: Set[com.histmersion.api.catalogue.Geocode.Apostrophes] = ("'`\u00b4\u2018\u2019\u02bc\u02b9".toSet).map(refactorValue2 => _root_.com.histmersion.api.catalogue.Geocode.Apostrophe(refactorValue2))

  private val Aside = "\\([^)]*\\)".r

  def key(s: String): com.histmersion.api.catalogue.Geocode.Key =
    val b = new StringBuilder((s).length)
    Aside.replaceAllIn((s), " ").foreach { c =>
      if Apostrophes.contains(_root_.com.histmersion.api.catalogue.Geocode.Apostrophes(_root_.com.histmersion.api.catalogue.Geocode.Apostrophe(c))) then ()
      else if Separators.contains(_root_.com.histmersion.api.catalogue.Geocode.Separators(_root_.com.histmersion.api.catalogue.Geocode.Separator(c))) then b += ' '
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
    def key: com.histmersion.api.catalogue.Geocode.Key = Geocode.key(name)

  object Attempt:

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Within = Within.T
    object Within extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val QualifierWords: Set[com.histmersion.api.catalogue.Geocode.QualifierWords] = Set(
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("north"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("south"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("east"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("west"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("northern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("southern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("eastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("western"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("northeast"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("northwest"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("southeast"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("southwest"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("northeastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("northwestern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("southeastern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("southwestern"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("central"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("upper"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("lower"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("inner"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("outer"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("greater"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("coastal"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("interior"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("middle"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("mid"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("highland"),
    _root_.com.histmersion.api.catalogue.Geocode.QualifierWord("lowland")
  )

  private val Joiners: Set[com.histmersion.api.catalogue.Geocode.Joiners] = Set(_root_.com.histmersion.api.catalogue.Geocode.Joiner("and"), _root_.com.histmersion.api.catalogue.Geocode.Joiner("or"))

  def stripQualifier(name: com.histmersion.api.catalogue.Geocode.Attempt.Name): Option[(com.histmersion.api.catalogue.Geocode.Attempt.Name, String)] =
    val words = (name).value.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: String) = QualifierWords.contains(key(w))
    @annotation.tailrec
    def go(rest: List[String], dropped: List[String]): (List[String], List[String]) = rest match
      case w :: tail if isQ(w)                                                      => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j)) && isQ(w) => go(tail, dropped :+ j :+ w)
      case _                                                                        => (dropped, rest)
    val (dropped, rest) = go((words).map(refactorValue6 => (refactorValue6)), Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && (key((rest.mkString(" ")))).value.nonEmpty)(
      (_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(dropped.mkString(" ")), (rest.mkString(" ")))
    )

  def compassBox(dropped: String): Option[(com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction)] =
    val words = (dropped).split("[\\s\\-]+").map({ val refactorFunction = key; (refactorValue7) => refactorFunction((refactorValue7)) }).filter((_).value.nonEmpty).toList
    val dirs = words.flatMap(w => Compass.get(_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction((w).value.stripSuffix("ern"))).orElse(Compass.get(_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction((w).value))))
    dirs match
      case List(box) => Some({ val refactorTuple10 = box; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction((refactorTuple10._1).value), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction((refactorTuple10._2).value), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction((refactorTuple10._3).value), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction((refactorTuple10._4).value)) })
      case _         => None

  private val Compass: Map[com.histmersion.api.catalogue.Geocode.Compass.Direction, (com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction, com.histmersion.api.catalogue.Geocode.Compass.Fraction)] = Map(
    { val refactorTuple = "north" -> (0.0, 0.5, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple._1), { val refactorTuple2 = refactorTuple._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple2._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple2._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple2._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple2._4)) }) },
    { val refactorTuple3 = "south" -> (0.0, 0.0, 1.0, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple3._1), { val refactorTuple4 = refactorTuple3._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple4._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple4._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple4._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple4._4)) }) },
    { val refactorTuple5 = "east" -> (0.5, 0.0, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple5._1), { val refactorTuple6 = refactorTuple5._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple6._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple6._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple6._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple6._4)) }) },
    { val refactorTuple7 = "west" -> (0.0, 0.0, 0.5, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple7._1), { val refactorTuple8 = refactorTuple7._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple8._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple8._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple8._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple8._4)) }) },
    { val refactorTuple31 = "northeast" -> (0.5, 0.5, 1.0, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple31._1), { val refactorTuple32 = refactorTuple31._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple32._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple32._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple32._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple32._4)) }) },
    { val refactorTuple33 = "northwest" -> (0.0, 0.5, 0.5, 1.0); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple33._1), { val refactorTuple34 = refactorTuple33._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple34._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple34._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple34._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple34._4)) }) },
    { val refactorTuple13 = "southeast" -> (0.5, 0.0, 1.0, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple13._1), { val refactorTuple14 = refactorTuple13._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple14._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple14._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple14._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple14._4)) }) },
    { val refactorTuple15 = "southwest" -> (0.0, 0.0, 0.5, 0.5); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple15._1), { val refactorTuple16 = refactorTuple15._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple16._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple16._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple16._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple16._4)) }) },
    { val refactorTuple17 = "central" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple17._1), { val refactorTuple18 = refactorTuple17._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple18._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple18._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple18._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple18._4)) }) },
    { val refactorTuple41 = "mid" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple41._1), { val refactorTuple42 = refactorTuple41._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple42._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple42._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple42._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple42._4)) }) },
    { val refactorTuple43 = "middle" -> (0.25, 0.25, 0.75, 0.75); (_root_.com.histmersion.api.catalogue.Geocode.Compass.Direction(refactorTuple43._1), { val refactorTuple44 = refactorTuple43._2; (_root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple44._1), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple44._2), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple44._3), _root_.com.histmersion.api.catalogue.Geocode.Compass.Fraction(refactorTuple44._4)) }) }
  )

  private val Asides = "\\(([^)]*)\\)".r

  def attempts(name: com.histmersion.api.catalogue.Geocode.Attempt.Name): List[Attempt] =
    val n = (name).value.trim
    if (key((n))).value.isEmpty then Nil
    else
      val asides = Asides.findAllMatchIn(n).map(_.group(1).trim).filter(a => (key((a))).value.nonEmpty).map(Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(_))).toList
      val comma = n.lastIndexOf(',') match
        case i if i > 0 && (key((n.take(i)))).value.nonEmpty && (key((n.drop(i + 1)))).value.nonEmpty =>
          Some((n.take(i).trim, n.drop(i + 1).trim))
        case _ => None
      val head = comma.fold(n)(_._1)
      val qualified =
        stripQualifier(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(head)).map((q, base) => Attempt(base, comma.map((refactorCallback => _root_.com.histmersion.api.catalogue.Geocode.Attempt.Within(refactorCallback._2))), Some(Approximation.Qualifier(q))))
      val all = List(Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(n))) ++ asides ++
        comma.map((a, in) => Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(a), Some(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Within(in)))).toList ++
        qualified.toList ++
        comma.map((a, in) => Attempt(_root_.com.histmersion.api.catalogue.Geocode.Attempt.Name(in), None, Some(Approximation.OnlyWithin(_root_.com.histmersion.api.catalogue.Geocode.Approximation.OnlyWithin.Missing(a))))).toList
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
    def undated: Boolean = (from.isEmpty && to.isEmpty)

    def span: com.histmersion.api.catalogue.Geocode.Candidate.Span = _root_.com.histmersion.api.catalogue.Geocode.Candidate.Span((for a <- from; b <- to yield (b).value - (a).value).getOrElse((_root_.com.histmersion.api.catalogue.Geocode.Candidate.Span(Long.MaxValue / 4)).value))
    def overlaps(p: Period): Boolean = from.forall(_ <= p.end) && to.forall(_ >= p.start)

    def gap(p: Period): com.histmersion.api.catalogue.Geocode.Candidate.Gap =
      if (overlaps(p)) then _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L)
      else to.filter((_).value < p.start).map((p.start).value - _).orElse(from.filter((_).value > p.end).map((_).value - p.end)).getOrElse(_root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))

  object Candidate:
    type Gap = Gap.T
    object Gap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Span = Span.T
    object Span extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    kind == _root_.com.histmersion.api.catalogue.Geocode.BroadKind(com.histmersion.api.catalogue.Place.Kind.Region) || kind == _root_.com.histmersion.api.catalogue.Geocode.BroadKind(com.histmersion.api.catalogue.Place.Kind.Polity)

  def tierOf(importSource: Option[com.histmersion.api.catalogue.Geocode.TierOfImportSource]): com.histmersion.api.catalogue.Geocode.Candidate.Tier = Gazetteer.rank(Place.Match(_root_.com.histmersion.api.catalogue.Place.Id(""), importSource))

  def isPlaceholder(c: Candidate, placeholderMin: com.histmersion.api.catalogue.Geocode.Placeholder.Min): Boolean = c.dimension == _root_.com.histmersion.api.catalogue.Geocode.Candidate.Dimension(0) && c.shared >= placeholderMin

  private def kindRank(kind: com.histmersion.api.catalogue.Geocode.KindRankKind, hint: KindHint): com.histmersion.api.catalogue.Geocode.KindRank = hint match
    case KindHint.Site =>
      if kind == _root_.com.histmersion.api.catalogue.Geocode.KindRankKind(com.histmersion.api.catalogue.Place.Kind.Site) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0)
      else if kind == _root_.com.histmersion.api.catalogue.Geocode.KindRankKind(com.histmersion.api.catalogue.Place.Kind.Feature) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)
    case KindHint.Water =>
      if kind == _root_.com.histmersion.api.catalogue.Geocode.KindRankKind(com.histmersion.api.catalogue.Place.Kind.Feature) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0)
      else if kind == _root_.com.histmersion.api.catalogue.Geocode.KindRankKind(com.histmersion.api.catalogue.Place.Kind.Site) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1)
      else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)
    case _ => if broad(kind) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(0) else if kind == _root_.com.histmersion.api.catalogue.Geocode.KindRankKind(com.histmersion.api.catalogue.Place.Kind.Feature) then _root_.com.histmersion.api.catalogue.Geocode.KindRank(1) else _root_.com.histmersion.api.catalogue.Geocode.KindRank(2)

  private def timeRank(c: Candidate, at: Option[Period]): (com.histmersion.api.catalogue.Geocode.TimeRank.Class, com.histmersion.api.catalogue.Geocode.Candidate.Gap) = at match
    case None    => if ((c.undated) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(0), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L)) else (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(1), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(-(c.span).value))
    case Some(p) =>
      if ((c.undated) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(1), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))
      else if ((c.overlaps(p)) == (true)) then (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(0), _root_.com.histmersion.api.catalogue.Geocode.Candidate.Gap(0L))
      else (_root_.com.histmersion.api.catalogue.Geocode.TimeRank.Class(2), c.gap(p))

  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: com.histmersion.api.catalogue.Geocode.RankPlaceholderMin): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (
        if ((isPlaceholder(c, placeholderMin)) == (true)) || ((c.gridCell) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(true)) then 1 else 0,
        if ((c.byLabel) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.ByLabel(true)) then 0 else 1,
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
    def isApproximate: Boolean = (approximate.nonEmpty)

    def describe: String =
      val approx = if approximate.isEmpty then "" else s" (approximate: ${approximate.map(_.describe).mkString("; ")})"
      (s"'$asked' → $label [$placeId]$approx")

  object Resolution:

    type Asked = Asked.T
    object Asked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def choose(asked: com.histmersion.api.catalogue.Geocode.ChooseAsked, attempt: Attempt, ranked: List[Candidate], placeholderMin: com.histmersion.api.catalogue.Geocode.ChoosePlaceholderMin): Option[Resolution] =
    ranked.headOption.map { c =>
      val rough = Option.when(((isPlaceholder(c, placeholderMin)) == (true)))(Approximation.Rough(c.shared))
      val grid = Option.when(((c.gridCell) == _root_.com.histmersion.api.catalogue.Geocode.Candidate.GridCell(true)) && rough.isEmpty)(Approximation.GridSquare)
      Resolution(asked, c.id, c.label, c.lon, c.lat, attempt.approx.toList ++ rough.toList ++ grid.toList)
    }

  def distanceKm(lon1: com.histmersion.api.catalogue.Geocode.Longitude, lat1: com.histmersion.api.catalogue.Geocode.Latitude, lon2: com.histmersion.api.catalogue.Geocode.Longitude, lat2: com.histmersion.api.catalogue.Geocode.Latitude): com.histmersion.api.catalogue.Geocode.DistanceKm =
    val r = 6371.0
    val dLat = math.toRadians(((lat2).value - (lat1).value))
    val dLon = math.toRadians(((lon2).value - (lon1).value))
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians((lat1).value)) * math.cos(math.toRadians(lat2)) * math.pow(math.sin(dLon / 2), 2)
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

  def worthMoving(m: Move, minKm: com.histmersion.api.catalogue.Geocode.Move.MinKm): Boolean = (m.km).value > minKm

  final case class Placement(findingId: com.histmersion.api.library.Finding.Id, citekey: com.histmersion.api.library.Citekey, region: Placement.Region, to: Resolution):
    def describe: String =
      String.format(
        java.util.Locale.ROOT,
        "%s: '%s' → %s at %.2f N %.2f E%s",
        findingId,
        region,
        to.label + " [" + to.placeId + "]",
        to.lat,
        to.lon,
        if ((to.isApproximate) == (true)) then s" (approximate: ${to.approximate.map(_.describe).mkString("; ")})" else ""
      )

  object Placement:

    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def periodOf(from: Option[com.histmersion.api.catalogue.Year], to: Option[com.histmersion.api.catalogue.Year]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(math.min((a).value, (b).value), math.max((a).value, (b).value)))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
