package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.ProviderDoctor
import com.histmersion.api.automation.agents.DoctorCheck
import com.histmersion.api.json.TwoStates

final case class DoctorReport(
    enabled: DoctorReport.Enabled,
    online: DoctorReport.Online,
    providers: List[ProviderDoctor],
    checks: List[DoctorCheck]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DoctorReport:
  enum Enabled derives CanEqual:
    case Enabled, Disabled
  object Enabled extends TwoStates[Enabled]:
    extension (enabled: Enabled) def enabled: Boolean = enabled == Enabled
  enum Online derives CanEqual:
    case Online, Offline
  object Online extends TwoStates[Online]:
    extension (online: Online) def online: Boolean = online == Online
