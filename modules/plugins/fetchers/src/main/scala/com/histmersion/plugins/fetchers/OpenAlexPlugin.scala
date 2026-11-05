package com.histmersion.plugins.fetchers

/** `oa-openalex`: OpenAlex's open-access locations of a work, by DOI. */
import com.histmersion.plugin.Plugin
final class OpenAlexPlugin extends FetcherPlugin("oa-openalex"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[com.histmersion.plugins.fetchers.OpenAlexPlugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers.add("openalex", OpenAccess(c.acquire, () => ctx.http, ctx.log).openalex)

object OpenAlexPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
