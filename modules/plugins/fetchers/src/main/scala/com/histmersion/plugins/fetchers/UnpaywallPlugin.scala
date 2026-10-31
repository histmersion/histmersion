package com.histmersion.plugins.fetchers

import com.histmersion.plugin.PluginContext

/** `oa-unpaywall`: Unpaywall's best open-access location, by DOI, asked with the plugin's contact (else Histmersion's).
  */
final class UnpaywallPlugin extends FetcherPlugin("oa-unpaywall"):
  type Config = UnpaywallConfig
  def register(c: UnpaywallConfig, ctx: PluginContext): Either[String, Unit] =
    ctx.sourceFetchers.add("unpaywall", OpenAccess(c.acquire, () => ctx.http, ctx.log).unpaywall(c.email))
