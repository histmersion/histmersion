package com.histmersion.plugins.fetchers

/** `oa-unpaywall`: Unpaywall's best open-access location, by DOI, asked with the plugin's contact (else Histmersion's).
  */
import com.histmersion.plugin.Plugin
final class UnpaywallPlugin extends FetcherPlugin("oa-unpaywall"):
  type Config = UnpaywallConfig
  def register(c: UnpaywallConfig, ctx: Plugin.Context): Either[com.histmersion.plugin.Plugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers.add(com.histmersion.plugin.Registry.AddId("unpaywall"), OpenAccess(c.acquire, () => ctx.http, ctx.log).unpaywall(c.email))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
