package com.histmersion.plugins.fetchers

import com.histmersion.plugin.PluginContext

/** `oa-landing`: the repository landing pages the open-access indexes and DataCite name for a DOI, resolved one step. */
final class LandingPlugin extends FetcherPlugin("oa-landing"):
  type Config = LandingConfig
  def register(c: LandingConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.sourceFetchers.add("landing", OpenAccess(c.acquire, () => ctx.http, ctx.log).landing(c.unpaywallEmail))
