package com.histmersion
package api.library
import com.histmersion.util.nonempty.NonEmptyVector

import com.histmersion.api.given
import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A sync: the gaps it considered, and with `apply` each push made. */
final case class CollectionSync(
    gaps: CollectionGaps,
    applied: CollectionSync.Applied,
    pushes: List[CollectionSync.Push]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionSync:

  /** One push of a sync: the item it became, or why it failed. */
  type Applied = Applied.T
  object Applied extends Newtype[Boolean, Pure]

  final case class Push(
      citekey: Citekey,
      id: Option[Push.Id],
      problems: Option[NonEmptyVector[Problem]]
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Push:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  /** Push every counted gap into a collection (a dry run unless `apply`), considering at most `limit` gaps. */
  final case class Request(
      apply: Request.Apply = Request.Apply(false),
      limit: Option[Request.Limit] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object Request:
    type Apply = Apply.T
    object Apply extends Newtype[Boolean, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
