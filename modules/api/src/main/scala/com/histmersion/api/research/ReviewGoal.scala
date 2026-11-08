package com.histmersion
package api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewGoal(slug: ReviewGoal.Slug, title: ReviewGoal.Title, status: ReviewGoal.Status) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object ReviewGoal:
  type Slug = Slug.T
  object Slug extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  export com.histmersion.api.automation.Goal.{StatusValue as Status}
