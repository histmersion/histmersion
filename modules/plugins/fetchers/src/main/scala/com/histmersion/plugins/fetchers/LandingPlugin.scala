package com.histmersion.plugins.fetchers

/** `oa-landing`: the repository landing pages the open-access indexes and DataCite name for a DOI, resolved one step.
  */
import com.histmersion.plugin.Plugin
final class LandingPlugin extends FetcherPlugin("oa-landing"):
  type Config = LandingConfig
  def register(c: LandingConfig, ctx: Plugin.Context): Either[String, Unit] =
    ctx.sourceFetchers.add("landing", OpenAccess(c.acquire, () => ctx.http, ctx.log).landing(c.unpaywallEmail))
