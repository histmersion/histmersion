package com.histmersion
package api.media

import com.histmersion.api.library.repairacquire.RepairRequest
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.RepairAcquire
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class MediaCoversRequest(
    dryRun: RepairRequest.DryRun = RepairRequest.DryRun.DryRun,
    limit: Option[MediaCoversRequest.Limit] = None,
    citekey: Option[Citekey] = None,
    refresh: MediaImportRequest.Refresh = MediaImportRequest.Refresh.Reuse,
    covers: MediaCoversRequest.Covers = MediaCoversRequest.Covers.Included,
    illustrations: MediaCoversRequest.Illustrations = MediaCoversRequest.Illustrations.Included,
    isbn: Option[MediaCoversRequest.Isbn] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object MediaCoversRequest:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  enum Covers derives CanEqual:
    case Included, Skipped
  object Covers extends TwoStates[Covers]:
    extension (covers: Covers) def included: Boolean = covers == Included
  enum Illustrations derives CanEqual:
    case Included, Skipped
  object Illustrations extends TwoStates[Illustrations]:
    extension (illustrations: Illustrations) def included: Boolean = illustrations == Included
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]
