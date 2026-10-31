package com.histmersion.plugins.fetchers

import com.histmersion.plugin.PluginContext

/** `arxiv`: an arXiv preprint's PDF, by its arXiv id. */
final class ArxivPlugin extends FetcherPlugin("arxiv"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: PluginContext): Either[String, Unit] =
    ctx.sourceFetchers.add("arxiv", ArxivFetcher(c.acquire, () => ctx.http))
