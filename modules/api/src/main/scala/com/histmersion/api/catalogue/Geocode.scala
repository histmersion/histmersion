package com.histmersion
package api.catalogue

import com.histmersion.api.catalogue.geocode.Candidate
import com.histmersion.api.catalogue.geocode.KindHint
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.{LatinFold, WireEnum}
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Citekey
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

/** Pleiades snaps imprecise places to stand-in points: 17 places, three of them "Mesopotamia", share 37.5 N 37.5 E, and
  * some sit on the centre of an atlas grid square ([[LocationPrecision.GridCell]]). Such points rank below every
  * precise candidate.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object Geocode:
  object TimeRank:
    type Class = Class.T
    object Class extends Newtype[Int, Pure]
  object Placeholder:
    type Min = Min.T
    object Min extends Newtype[Int, Pure]
  object Compass:
    type Fraction = Fraction.T
    object Fraction extends Newtype[Double, Pure]
  type DistanceKm = DistanceKm.T
  object DistanceKm extends Newtype[Double, Pure]
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type KindRank = KindRank.T
  object KindRank extends Newtype[Int, Pure]

  /** Must equal the separator list of the SQL function `histmersion_place_key`. */
  val Separators: Set[Char] =
    (" \u00a0\t\n\r!\"#$%&()*+,-./:;<=>?@[\\]^_{|}~" +
      "\u2010\u2011\u2012\u2013\u2014\u2015\u201c\u201d\u201e\u00ab\u00bb\u00b7\u2026").toSet

  /** Apostrophe-likes, dropped ("Rus'" = "Rus"). Must equal the SQL function `histmersion_place_key`'s. */
  val Apostrophes: Set[Char] = "'`\u00b4\u2018\u2019\u02bc\u02b9".toSet

  private val Aside = "\\([^)]*\\)".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key(s: String): Key =
    val b = new StringBuilder((s).length)
    Aside.replaceAllIn((s), " ").foreach { c =>
      if Apostrophes.contains(c) then ()
      else if Separators.contains(c) then b += ' '
      else LatinFold.foldChar((c)).foreach(f => b += (if f >= 'A' && f <= 'Z' then (f + 32).toChar else f))
    }
    val spaced = b.result().replaceAll(" {2,}", " ").dropWhile(_ == ' ').reverse.dropWhile(_ == ' ').reverse
    if spaced.startsWith("the ") then Key(spaced.drop(4)) else Key(spaced)

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def broad(kind: Candidate.Kind) =
    kind.value == Place.Kind.Region || kind.value == Place.Kind.Polity

  def tierOf(importSource: Option[Place.Match.ImportSource]): Gazetteer.Rank =
    Gazetteer.rank(Place.Match(Place.Id(""), importSource))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def timeRank(c: Candidate, at: Option[Period]): (TimeRank.Class, Candidate.Gap) = at match
    case None =>
      if (c.undated) == (true) then (TimeRank.Class(0), Candidate.Gap(0L))
      else (TimeRank.Class(1), Candidate.Gap(-(c.span).value))
    case Some(p) =>
      if (c.undated) == (true) then (TimeRank.Class(1), Candidate.Gap(0L))
      else if (c.overlaps(p)) == (true) then (TimeRank.Class(0), Candidate.Gap(0L))
      else (TimeRank.Class(2), c.gap(p))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rank(cs: List[Candidate], hint: KindHint, at: Option[Period], placeholderMin: Placeholder.Min): List[Candidate] =
    cs.sortBy { c =>
      val (t, gap) = timeRank(c, at)
      (
        if ((c.isPlaceholder(placeholderMin)) == (true)) || ((c.gridCell) == Candidate.GridCell.GridCell) then 1
        else 0,
        if (c.byLabel) == Candidate.ByLabel.ByLabel then 0 else 1,
        c.tier.value: Int,
        kindRank(c.kind, hint).value: Int,
        if broad(c.kind) && c.dimension.value == 0 then 1 else 0,
        t.value: Int,
        gap.value: Long,
        c.area.value: Double,
        c.id.value: String
      )
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def distanceKm(lon1: Longitude, lat1: Latitude, lon2: Longitude, lat2: Latitude): DistanceKm =
    val r = 6371.0
    val dLat = math.toRadians(((lat2).value - (lat1).value))
    val dLon = math.toRadians(((lon2).value - (lon1).value))
    val a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(math.toRadians((lat1).value)) * math.cos(math.toRadians((lat2).value)) * math.pow(math.sin(dLon / 2), 2)
    DistanceKm(2 * r * math.asin(math.min(1.0, math.sqrt(a))))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def periodOf(from: Option[Year], to: Option[Year]): Option[Period] = (from, to) match
    case (Some(a), Some(b)) => Some(Period(Year(math.min((a).value, (b).value)), Year(math.max((a).value, (b).value))))
    case (Some(a), None)    => Some(Period(a, a))
    case (None, Some(b))    => Some(Period(b, b))
    case _                  => None
