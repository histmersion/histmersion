package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
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
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object ReviewBadge:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
  type OpenMajors = OpenMajors.T
  object OpenMajors extends Newtype[Int, Pure]
  type OpenMinors = OpenMinors.T
  object OpenMinors extends Newtype[Int, Pure]
  type Findings = Findings.T
  object Findings extends Newtype[Int, Pure]
  type Runs = Runs.T
  object Runs extends Newtype[Int, Pure]
  export ReviewRound.Round
