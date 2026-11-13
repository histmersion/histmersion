package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** An update of every linked item: one row per item, in id order, and whether it was written (`applied`). */
final case class CollectionUpdateAll(
    rows: List[CollectionUpdateRow],
    applied: CollectionUpdateAll.Applied
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionUpdateAll:

  /** Plan the rewrite of every linked item from its record; with `apply`, write it. */
  type Applied = Applied.T
  object Applied extends Newtype[Boolean, Pure]

  final case class Request(
      apply: Request.Apply = Request.Apply(false)
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Request:
    type Apply = Apply.T
    object Apply extends Newtype[Boolean, Pure]
