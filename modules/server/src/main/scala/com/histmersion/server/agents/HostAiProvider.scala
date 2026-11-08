package com.histmersion
package server.agents

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.pluginhost.HostOwned
import com.histmersion.domain.Title

/** An AI provider that is Histmersion's OWN code (the three CLI runtimes), registered through the plugin host like any
  * other (docs/research-plugins.md §22, D2) but run by the host's own clients with its `agents.providers.<name>.*`
  * settings, unchanged and read at each call. The SPI's `turn` is never called for it.
  */
final case class HostAiProvider(plugin: HostAiProvider.Plugin, kind: ProviderKind) extends AiProvider, HostOwned:
  def title: AiProvider.TitleResult = AiProvider.TitleResult(BuiltinAi.titles.getOrElse(kind, Title(kind.wire.value)).value)
  def turn(request: AiTurn.Request, http: AiHttp): Either[AiFailure, AiTurn] =
    Left(
      AiFailure.Error(
        AiFailure.Error.Message(s"${kind.wire} is Histmersion's own provider; it is run by the host, not through the SPI")
      )
    )

object HostAiProvider:

  type Plugin = com.histmersion.util.plugins.Plugin.Id
  val Plugin: com.histmersion.util.plugins.Plugin.Id.type = com.histmersion.util.plugins.Plugin.Id
