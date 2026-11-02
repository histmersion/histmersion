package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{Access, Fetch, FetchContext, PluginHttp, SourceFetcher, WorkKey}
import com.histmersion.upstreams.settings.UpstreamSettings

/** The `arxiv` plugin's fetcher: an arXiv preprint's PDF, by its arXiv id. */
final class ArxivFetcher(cfg: UpstreamSettings, http: () => PluginHttp) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    work.arxiv.fold[Fetch](Fetch.Miss("arxiv needs an arXiv id")) { id =>
      Downloads(http())
        .pdf(ctx.log, "arxiv", s"${cfg.arxiv.url}/pdf/$id")
        .fold(Fetch.Miss(s"arxiv has no PDF of $id"))(Fetch.Found(_))
    }
