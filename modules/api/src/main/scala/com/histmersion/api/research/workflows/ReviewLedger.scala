package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewLedger(
    slug: ReviewLedger.Slug,
    entries: List[LedgerEntry] = Nil,
    lessons: List[ReviewLesson] = Nil,
    task: Option[ReviewLedger.Task] = None
) derives JsonCodec

object ReviewLedger:
  type Task = Task.T
  object Task extends Newtype[Int, Pure]
  type Slug = Slug.T
  object Slug extends Newtype[String, Pure]
