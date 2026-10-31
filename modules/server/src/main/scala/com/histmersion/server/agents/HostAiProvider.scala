package com.histmersion.server.agents

import com.histmersion.api.Agents.ProviderKind
import com.histmersion.plugin.*
import com.histmersion.pluginhost.HostOwned

/** An AI provider that is Histmersion's OWN code (the three CLI runtimes), registered through the plugin host like any
  * other (docs/research-plugins.md §22, D2) but run by the host's own clients with its `agents.providers.<name>.*`
  * settings, unchanged and read at each call. The SPI's `turn` is never called for it.
  */
final case class HostAiProvider(plugin: String, kind: ProviderKind) extends AiProvider, HostOwned:
  def title: String = BuiltinAi.titles.getOrElse(kind, kind.wire)
  def turn(request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] =
    Left(AiFailure.Error(s"${kind.wire} is Histmersion's own provider; it is run by the host, not through the SPI"))
