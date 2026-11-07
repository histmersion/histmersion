package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.plugin.Plugin

/** The `arxiv` plugin's fetcher: an arXiv preprint's PDF, by its arXiv id. */
final class ArxivFetcher(cfg: UpstreamSettings, http: () => Plugin.Http) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.arxiv.fold[Fetch](Fetch.Miss(com.histmersion.plugin.Fetch.Miss.Why("arxiv needs an arXiv id"))) { id =>
      Downloads(http())
        .pdf(ctx.log, com.histmersion.plugin.FetchedFile.Source("arxiv"), Plugin.Http.GetUrl(s"${cfg.arxiv.url}/pdf/$id"))
        .fold[Fetch](Fetch.Miss(Fetch.Miss.Why(s"arxiv has no PDF of $id")))(Fetch.Found(_))
    }
