package com.histmersion.plugins.fetchers

import com.histmersion.plugin.PluginContext

/** `oa-core`: CORE's download URLs for a DOI, with an API key, through the server's CORE budget. */
final class CorePlugin extends FetcherPlugin("oa-core"):
  type Config = CoreConfig
  def register(c: CoreConfig, ctx: PluginContext): Either[String, Unit] =
    c.apiKey
      .fold[Either[String, Option[String]]](Right(c.acquire.core.key))(s =>
        ctx.secret(s).map(v => Option(v).map(_.trim).filter(_.nonEmpty))
      )
      .flatMap(key =>
        ctx.sourceFetchers.add("core", OpenAccess(c.acquire, () => ctx.http, ctx.log).core(key, ctx.quota("core")))
      )
