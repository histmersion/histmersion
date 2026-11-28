package com.histmersion
package api.automation.approvaldigest

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.Approval

final case class BulkDecide(
    decision: BulkDecide.Decision,
    kind: Option[BulkDecide.Kind] = None,
    subject: Option[BulkDecide.Subject] = None,
    ids: List[Open.Id] = Nil,
    note: Option[BulkDecide.Note] = None,
    dryRun: BulkDecide.DryRun = BulkDecide.DryRun.Apply,
    limit: Option[BulkDecide.Limit] = None,
    by: Option[BulkDecide.By] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object BulkDecide:
  type Decision = Decision.T
  object Decision extends Newtype[String, Pure]
  export Approval.Kind.Wire as Kind
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  export Approval.Note
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
