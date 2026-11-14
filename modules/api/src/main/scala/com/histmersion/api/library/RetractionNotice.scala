package com.histmersion
package api.library

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class RetractionNotice(
    nature: Option[RetractionNotice.Nature],
    reason: Option[RetractionNotice.Account],
    date: Option[RetractionNotice.Date]
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object RetractionNotice:
  /** Why the work was retracted, in Retraction Watch's words: outside text, not a failure. */
  type Account = Account.T
  object Account extends Newtype[String, Pure]
  type Nature = Nature.T
  object Nature extends Newtype[String, Pure]
  type Date = Date.T
  object Date extends Newtype[String, Pure]
