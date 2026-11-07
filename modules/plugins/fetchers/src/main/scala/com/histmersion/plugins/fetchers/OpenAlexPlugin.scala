package com.histmersion.plugins.fetchers

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
import com.histmersion.plugin.Plugin
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers
      .add(com.histmersion.plugin.Registry.AddId("openalex"), OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
