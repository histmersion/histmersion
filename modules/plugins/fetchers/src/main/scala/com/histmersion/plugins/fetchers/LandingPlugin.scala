package com.histmersion
package plugins.fetchers

/** `oa-landing`: the repository landing pages the open-access indexes and DataCite name for a DOI, resolved one step.
  */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class LandingPlugin extends FetcherPlugin("oa-landing"):
  type Config = LandingConfig
  def register(c: LandingConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers
      .add(Registry.AddId("landing"), OpenAccess(c.acquire, () => ctx.http, ctx.log).landing(c.unpaywallEmail))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
