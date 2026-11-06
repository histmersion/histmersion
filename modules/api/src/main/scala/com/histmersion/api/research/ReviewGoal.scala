package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ReviewGoal(slug: ReviewGoal.Slug, title: ReviewGoal.Title, status: ReviewGoal.Status) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object ReviewGoal:
  type Slug = com.histmersion.api.research.ReviewGoal.Slug
  val Slug: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = com.histmersion.api.Api.GoalQuery.Status.Wire
  val Status: _root_.com.histmersion.api.Api.GoalQuery.Status.Wire.type = _root_.com.histmersion.api.Api.GoalQuery.Status.Wire
