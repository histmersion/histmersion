package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.exchange.IssueSide

final case class ReviewGoal(slug: ReviewGoal.Slug, title: ReviewGoal.Title, status: ReviewGoal.Status)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReviewGoal:
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
    extension (slug: Slug) def asSideLabel: IssueSide.Label = IssueSide.Label(slug.value)
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  export api.automation.Goal.StatusValue as Status
