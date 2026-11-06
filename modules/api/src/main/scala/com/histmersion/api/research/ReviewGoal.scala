package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ReviewGoal(slug: ReviewGoal.Slug, title: ReviewGoal.Title, status: ReviewGoal.Status) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object ReviewGoal:
  type Slug = Slug.T
  object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = com.histmersion.api.Api.GoalQuery.Status.Wire
  val Status: _root_.com.histmersion.api.Api.GoalQuery.Status.Wire.type = _root_.com.histmersion.api.Api.GoalQuery.Status.Wire
