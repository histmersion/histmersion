package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.{Problem, Problems}

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[Problems, Unit] =
    ctx.sourceFetchers
      .add(Registry.AddId("openalex"), OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)
