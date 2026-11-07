package com.histmersion.plugins.fetchers

/** `oa-landing`: the repository landing pages the open-access indexes and DataCite name for a DOI, resolved one step.
  */
import com.histmersion.plugin.Plugin
final class LandingPlugin extends FetcherPlugin("oa-landing"):
  type Config = LandingConfig
  def register(c: LandingConfig, ctx: Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers.add(com.histmersion.plugin.Registry.AddId("landing"), OpenAccess(c.acquire, () => ctx.http, ctx.log).landing(c.unpaywallEmail))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
