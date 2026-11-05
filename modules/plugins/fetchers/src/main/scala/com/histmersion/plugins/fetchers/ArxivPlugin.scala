package com.histmersion.plugins.fetchers

/** `arxiv`: an arXiv preprint's PDF, by its arXiv id. */
import com.histmersion.plugin.Plugin
final class ArxivPlugin extends FetcherPlugin("arxiv"):
  type Config = HostAcquire
  def register(c: HostAcquire, ctx: Plugin.Context): Either[com.histmersion.plugins.fetchers.ArxivPlugin.RegisterResultPart1, Unit] =
    ctx.sourceFetchers.add("arxiv", ArxivFetcher(c.acquire, () => ctx.http))

object ArxivPlugin:
  type RegisterResultPart1 = RegisterResultPart1.T
  object RegisterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
