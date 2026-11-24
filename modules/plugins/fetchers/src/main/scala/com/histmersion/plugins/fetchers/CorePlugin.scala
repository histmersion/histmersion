package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.{Problem, Problems}

/** `oa-core`: CORE's download URLs for a DOI, with an API key, through the server's CORE budget. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
import com.histmersion.lint.{LintRule, tolerated}
final class CorePlugin extends FetcherPlugin("oa-core"):
  type Config = CoreConfig
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def register(c: CoreConfig, ctx: Plugin.Context): Either[Problems, Unit] =
    c.apiKey
      .fold[Either[Problems, Option[OpenAccess.CoreKey]]](
        Right(c.acquire.core.key.map(k => OpenAccess.CoreKey(k.value)))
      )(s =>
        ctx
          .secret(s)
          .map(v => Option(v).map(_.value.trim).filter(_.nonEmpty).map(OpenAccess.CoreKey(_)))
      )
      .flatMap(key =>
        ctx.sourceFetchers
          .add(
            Registry.AddId("core"),
            OpenAccess(c.acquire, () => ctx.http, ctx.log).core(key, ctx.quota(Plugin.Context.QuotaUpstream("core")))
          )
      )
