package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

/** `arxiv`: an arXiv preprint's PDF, by its arXiv id. */
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Registry
final class ArxivPlugin extends FetcherPlugin("arxiv"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[Problem, Unit] =
    ctx.sourceFetchers
      .add(Registry.AddId("arxiv"), ArxivFetcher(c.acquire, () => ctx.http))
