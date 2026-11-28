package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.CheckLevel
import com.histmersion.api.automation.agents.DoctorCheck
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class ProviderDoctor(
    provider: ProviderDoctor.Provider,
    used: ProviderDoctor.Used,
    models: List[ProviderDoctor.Models],
    checks: List[DoctorCheck],
    upstreamModels: List[ProviderDoctor.UpstreamModels] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  def level: CheckLevel =
    if checks.exists(_.level == CheckLevel.Fail) then CheckLevel.Fail
    else if checks.exists(_.level == CheckLevel.Warn) then CheckLevel.Warn
    else CheckLevel.Ok

object ProviderDoctor:
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]
  enum Used derives CanEqual:
    case Used, Unused
  object Used extends TwoStates[Used]:
    extension (used: Used) def used: Boolean = used == Used
  type Models = Models.T
  object Models extends Newtype[String, Pure]
  type UpstreamModels = UpstreamModels.T
  object UpstreamModels extends Newtype[String, Pure]
