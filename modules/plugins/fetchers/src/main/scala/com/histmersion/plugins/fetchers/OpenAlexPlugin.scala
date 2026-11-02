package com.histmersion.plugins.fetchers

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
import com.histmersion.plugin.Plugin
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[String, Unit] =
    ctx.sourceFetchers.add("openalex", OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)
