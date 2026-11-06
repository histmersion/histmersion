package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** An update of every linked item: one row per item, in id order, and whether it was written (`applied`). */
final case class CollectionUpdateAll(
    rows: List[CollectionUpdateRow],
    applied: CollectionUpdateAll.Applied
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionUpdateAll:


  /** Plan the rewrite of every linked item from its record; with `apply`, write it. */
  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Request(
      apply: Request.Apply = _root_.com.histmersion.api.library.CollectionUpdateAll.Request.Apply(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Apply = Apply.T
    object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
