package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

/** Import item `id` of a collection into the library: under `citekey` (else the generated one), its `format` (else the
  * collection's preference), converted unless `convert` is false, with its cover when `cover`, ticking off acquisition
  * item `acquireItem` when the conversion succeeds.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CollectionImportRequest(
    id: CollectionImportRequest.Id,
    citekey: Option[Citekey] = None,
    format: Option[CollectionImportRequest.Format] = None,
    convert: CollectionImportRequest.Convert = CollectionImportRequest.Convert.Convert,
    cover: CollectionImportRequest.Cover = CollectionImportRequest.Cover.Cover,
    acquireItem: Option[CollectionImportRequest.AcquireItem] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object CollectionImportRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Format = Format.T
  object Format extends Newtype[String, Pure]
  enum Convert derives CanEqual:
    case Convert, AsIs
  object Convert extends TwoStates[Convert]:
    extension (convert: Convert) def convert: Boolean = convert == Convert
  enum Cover derives CanEqual:
    case Cover, NoCover
  object Cover extends TwoStates[Cover]:
    extension (cover: Cover) def cover: Boolean = cover == Cover
  type AcquireItem = AcquireItem.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object AcquireItem extends Newtype[Int, Pure]
