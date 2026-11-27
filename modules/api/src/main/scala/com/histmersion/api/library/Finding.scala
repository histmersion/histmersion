package com.histmersion
package api.library

import com.histmersion.util.problem.Problem

import com.histmersion.api.GeoPoint
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.StrictJson
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import scala.annotation.tailrec
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Place
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

final case class Finding(
    id: Finding.Id,
    citekey: Citekey,
    claim: Finding.Claim,
    region: Option[Finding.Region],
    geo: Option[GeoPoint],
    period: Option[Period],
    periodName: Option[Finding.PeriodName],
    tags: List[Finding.Tags],
    confidence: Option[Confidence],
    significance: Option[Finding.Significance] = None
)

object Finding:
  type Ordinal = Ordinal.T
  object Ordinal extends Newtype[Int, Pure]

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Region = Region.T
  object Region extends Newtype[String, Pure]
  type PeriodName = PeriodName.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object PeriodName extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type Significance = Significance.T
  object Significance extends Newtype[String, Pure]

  final case class Input(
      claim: Claim,
      region: Option[Region] = None,
      lon: Option[Longitude] = None,
      lat: Option[Latitude] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      periodName: Option[PeriodName] = None,
      tags: List[Tags] = Nil,
      confidence: Option[Input.Confidence] = None,
      significance: Option[Significance] = None
  ) derives StrictJson
  object Input:
    export com.histmersion.api.library.Confidence.Wire as Confidence

    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[Input] = KindlingsSchema
      .derived[Input]
      .schema
      .modify(_.confidence)(_.description("high | medium | low | contested — another word is refused"))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def of(f: Finding): Input = Input(
      claim = f.claim,
      region = f.region,
      lon = f.geo.map(_.lon),
      lat = f.geo.map(_.lat),
      periodStart = f.period.map(_.start),
      periodEnd = f.period.map(_.end),
      periodName = f.periodName,
      tags = f.tags,
      confidence = f.confidence.map(c => com.histmersion.api.library.Confidence.Wire(c.toString.toLowerCase)),
      significance = f.significance
    )

  final case class Match(
      id: Id,
      claim: Claim,
      regionName: Option[Place.Region],
      lon: Option[Longitude],
      lat: Option[Latitude],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      periodName: Option[PeriodName],
      tags: List[Tags],
      why: List[Hit.Rationale],
      places: List[Match.Places] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Match:
    type Places = Places.T
    object Places extends Newtype[String, Pure]

  final case class Row(
      claim: Claim,
      regionName: Option[Place.Region],
      lon: Option[Longitude],
      lat: Option[Latitude],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      periodName: Option[PeriodName],
      tags: List[Tags],
      confidence: Option[Confidence],
      significance: Option[Significance] = None,
      uid: Option[java.util.UUID] = None,
      creator: Option[Creation] = None,
      createdAt: Option[Creation.At] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema
