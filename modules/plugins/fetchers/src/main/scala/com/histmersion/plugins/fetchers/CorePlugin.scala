package com.histmersion
package plugins.fetchers

/** `oa-core`: CORE's download URLs for a DOI, with an API key, through the server's CORE budget. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class CorePlugin extends FetcherPlugin("oa-core"):
  type Config = CoreConfig
  def register(c: CoreConfig, ctx: Plugin.Context): Either[Plugin.RegisterResultPart1, Unit] =
    c.apiKey
      .fold[Either[Plugin.RegisterResultPart1, Option[OpenAccess.CoreKey]]](Right(c.acquire.core.key.map(k => OpenAccess.CoreKey(k.value))))(s =>
        ctx
          .secret(s)
          .left
          .map(e => Plugin.RegisterResultPart1(e.value))
          .map(v => Option(v).map(_.value.trim).filter(_.nonEmpty).map(OpenAccess.CoreKey(_)))
      )
      .flatMap(key =>
        ctx.sourceFetchers
          .add(
            Registry.AddId("core"),
            OpenAccess(c.acquire, () => ctx.http, ctx.log).core(key, ctx.quota(Plugin.Context.QuotaUpstream("core")))
          )
          .left
          .map(e => Plugin.RegisterResultPart1(e.value))
      )
