package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class SubjectCount(subject: SubjectCount.Subject, count: SubjectCount.Count)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SubjectCount:
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
