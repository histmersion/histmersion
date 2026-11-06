package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** Import item `id` of a collection into the library: under `citekey` (else the generated one), its `format` (else the
  * collection's preference), converted unless `convert` is false, with its cover when `cover`, ticking off acquisition
  * item `acquireItem` when the conversion succeeds.
  */
final case class CollectionImportRequest(
    id: CollectionImportRequest.Id,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    format: Option[CollectionImportRequest.Format] = None,
    convert: CollectionImportRequest.Convert = _root_.com.histmersion.api.library.CollectionImportRequest.Convert(true),
    cover: CollectionImportRequest.Cover = _root_.com.histmersion.api.library.CollectionImportRequest.Cover(true),
    acquireItem: Option[CollectionImportRequest.AcquireItem] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionImportRequest:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Format = Format.T
  object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Convert = Convert.T
  object Convert extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cover = Cover.T
  object Cover extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
