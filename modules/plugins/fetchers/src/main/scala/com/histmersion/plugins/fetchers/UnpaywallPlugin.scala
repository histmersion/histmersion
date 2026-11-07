package com.histmersion.plugins.fetchers

/** `oa-unpaywall`: Unpaywall's best open-access location, by DOI, asked with the plugin's contact (else Histmersion's).
  */
import com.histmersion.plugin.Plugin
final class UnpaywallPlugin extends FetcherPlugin("oa-unpaywall"):
  type Config = UnpaywallConfig
  def register(c: UnpaywallConfig, ctx: Plugin.Context): Either[com.histmersion.plugins.fetchers.UnpaywallPlugin.Problem, Unit] =
    ctx.sourceFetchers.add("unpaywall", OpenAccess(c.acquire, () => ctx.http, ctx.log).unpaywall(c.email))

object UnpaywallPlugin:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
