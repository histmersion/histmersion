package com.histmersion
package api.library
import com.histmersion.api.Problem
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A sync: the gaps it considered, and with `apply` each push made. */
final case class CollectionSync(
    gaps: CollectionGaps,
    applied: CollectionSync.Applied,
    pushes: List[CollectionSync.Push]
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionSync:

  /** One push of a sync: the item it became, or why it failed. */
  type Applied = Applied.T
  object Applied extends Newtype[Boolean, Pure]

  final case class Push(
      citekey: Citekey,
      id: Option[Push.Id],
      error: Option[Problem]
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Push:
    type Id = Id.T
    object Id extends Newtype[String, Pure]

  /** Push every counted gap into a collection (a dry run unless `apply`), considering at most `limit` gaps. */
  final case class Request(
      apply: Request.Apply = Request.Apply(false),
      limit: Option[Request.Limit] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Request:
    type Apply = Apply.T
    object Apply extends Newtype[Boolean, Pure]
    type Limit = Limit.T
    object Limit extends Newtype[Int, Pure]
