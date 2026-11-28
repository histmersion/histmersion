package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.problem.Problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class BulkFailed(id: BulkFailed.Id, problems: NonEmptyVector[Problem])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object BulkFailed:
  type Id = Id.T
  object Id extends Newtype[Long, Pure]
