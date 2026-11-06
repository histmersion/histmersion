package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ReviewBadge(
    state: ReviewState,
    label: ReviewBadge.Label,
    detail: ReviewBadge.Detail,
    openMajors: ReviewBadge.OpenMajors = _root_.com.histmersion.api.research.ReviewBadge.OpenMajors(0),
    openMinors: ReviewBadge.OpenMinors = _root_.com.histmersion.api.research.ReviewBadge.OpenMinors(0),
    findings: ReviewBadge.Findings = _root_.com.histmersion.api.research.ReviewBadge.Findings(0),
    runs: ReviewBadge.Runs = _root_.com.histmersion.api.research.ReviewBadge.Runs(0),
    round: Option[ReviewBadge.Round] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReviewBadge:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Detail = Detail.T
  object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMajors = OpenMajors.T
  object OpenMajors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenMinors = OpenMinors.T
  object OpenMinors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Findings = Findings.T
  object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Runs = Runs.T
  object Runs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Round = Round.T
  object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
