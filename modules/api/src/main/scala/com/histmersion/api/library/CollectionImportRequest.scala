package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Import item `id` of a collection into the library: under `citekey` (else the generated one), its `format` (else the
  * collection's preference), converted unless `convert` is false, with its cover when `cover`, ticking off acquisition
  * item `acquireItem` when the conversion succeeds.
  */
final case class CollectionImportRequest(
    id: CollectionImportRequest.Id,
    citekey: Option[Citekey] = None,
    format: Option[CollectionImportRequest.Format] = None,
    convert: CollectionImportRequest.Convert = CollectionImportRequest.Convert(true),
    cover: CollectionImportRequest.Cover = CollectionImportRequest.Cover(true),
    acquireItem: Option[CollectionImportRequest.AcquireItem] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionImportRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Format = Format.T
  object Format extends Newtype[String, Pure]
  type Convert = Convert.T
  object Convert extends Newtype[Boolean, Pure]
  type Cover = Cover.T
  object Cover extends Newtype[Boolean, Pure]
  type AcquireItem = AcquireItem.T
  object AcquireItem extends Newtype[Int, Pure]
