package com.histmersion
package api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewBadge(
    state: ReviewState,
    label: ReviewBadge.Label,
    detail: ReviewBadge.Detail,
    openMajors: ReviewBadge.OpenMajors = ReviewBadge.OpenMajors(0),
    openMinors: ReviewBadge.OpenMinors = ReviewBadge.OpenMinors(0),
    findings: ReviewBadge.Findings = ReviewBadge.Findings(0),
    runs: ReviewBadge.Runs = ReviewBadge.Runs(0),
    round: Option[ReviewBadge.Round] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReviewBadge:
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Detail = Detail.T
  object Detail extends RefinedType[String, Pure]
  type OpenMajors = OpenMajors.T
  object OpenMajors extends RefinedType[Int, Pure]
  type OpenMinors = OpenMinors.T
  object OpenMinors extends RefinedType[Int, Pure]
  type Findings = Findings.T
  object Findings extends RefinedType[Int, Pure]
  type Runs = Runs.T
  object Runs extends RefinedType[Int, Pure]
  type Round = ReviewRound.Round
  val Round: ReviewRound.Round.type = ReviewRound.Round
