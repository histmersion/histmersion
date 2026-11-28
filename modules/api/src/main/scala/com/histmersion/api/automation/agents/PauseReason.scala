package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum PauseReason(val wire: PauseReason.Wire) derives CanEqual:
  case Budget extends PauseReason(PauseReason.Wire("budget"))
  case Interrupted extends PauseReason(PauseReason.Wire("interrupted"))
  case Requested extends PauseReason(PauseReason.Wire("requested"))
  case Provider extends PauseReason(PauseReason.Wire("provider"))

  case Allowance extends PauseReason(PauseReason.Wire("allowance"))

  case PlanQuota extends PauseReason(PauseReason.Wire("plan-quota"))

  case Stalled extends PauseReason(PauseReason.Wire("stalled"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object PauseReason:
  export WireEnumValues.Wire

  given WireEnumValues[PauseReason]:
    def all: Array[PauseReason] = values
    def wire(e: PauseReason): WireEnumValues.Wire = e.wire
    def fromWire(s: WireEnumValues.Wire): Option[PauseReason] = values.find(_.wire == s)
  // without these, a record deriving its codec spells the enum as an object (ContractSnapshotSuite refuses that)
  given JsonValueCodec[PauseReason] = WireEnum.codec
  given Schema[PauseReason] = WireEnum.schema
