package com.histmersion
package api.library.repairacquire

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Creation
import com.histmersion.api.json.TwoStates

final case class RepairRequest(
    dryRun: RepairRequest.DryRun = RepairRequest.DryRun.Apply,
    by: Option[RepairRequest.By] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object RepairRequest:
  enum DryRun derives CanEqual:
    case DryRun, Apply
  object DryRun extends TwoStates[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  export api.automation.Activity.Actor as By
