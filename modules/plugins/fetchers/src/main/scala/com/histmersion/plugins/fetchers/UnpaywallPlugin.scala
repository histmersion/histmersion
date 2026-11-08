package com.histmersion
package plugins.fetchers

/** `oa-unpaywall`: Unpaywall's best open-access location, by DOI, asked with the plugin's contact (else Histmersion's).
  */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class UnpaywallPlugin extends FetcherPlugin("oa-unpaywall"):
  type Config = UnpaywallConfig
  def register(c: UnpaywallConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers
      .add(Registry.AddId("unpaywall"), OpenAccess(c.acquire, () => ctx.http, ctx.log).unpaywall(c.email))
      .left
      .map(e => Plugin.RegisterResultPart1(e.value))
