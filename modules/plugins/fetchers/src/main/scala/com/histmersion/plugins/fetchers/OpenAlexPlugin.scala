package com.histmersion.plugins.fetchers

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
import com.histmersion.plugin.Plugin
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[com.histmersion.plugins.fetchers.OpenAlexPlugin.Problem, Unit] =
    ctx.sourceFetchers.add(com.histmersion.plugin.Registry.AddId("openalex"), OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)

object OpenAlexPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
