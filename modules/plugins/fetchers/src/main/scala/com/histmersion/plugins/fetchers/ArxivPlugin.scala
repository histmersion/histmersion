package com.histmersion.plugins.fetchers

/** `arxiv`: an arXiv preprint's PDF, by its arXiv id. */
import com.histmersion.plugin.Plugin
final class ArxivPlugin extends FetcherPlugin("arxiv"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[String, Unit] =
    ctx.sourceFetchers.add("arxiv", ArxivFetcher(c.acquire, () => ctx.http))
