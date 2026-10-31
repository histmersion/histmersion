package com.histmersion.plugins.fetchers

import com.histmersion.plugin.PluginContext

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: PluginContext): Either[String, Unit] =
    ctx.sourceFetchers.add("openalex", OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)
