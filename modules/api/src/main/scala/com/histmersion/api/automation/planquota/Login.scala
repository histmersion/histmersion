package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.agents.DoctorCheck

enum Login:
  case Present(detail: Present.Detail)
  case Missing(why: Rationale)
  case Unknown(why: Rationale)

object Login:
  object Present:
    export DoctorCheck.Detail
