package com.histmersion.server.acquire.fetchers

import com.histmersion.config.AcquireConfig
import com.histmersion.plugin.{Access, Fetch, FetchContext, SourceFetcher, WorkKey}
import com.histmersion.server.acquire.Http

/** The `arxiv` plugin's fetcher: an arXiv preprint's PDF, by its arXiv id. */
final class ArxivFetcher(cfg: AcquireConfig, http: () => Http) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: FetchContext): Fetch =
    work.arxiv.fold[Fetch](Fetch.Miss("arxiv needs an arXiv id")) { id =>
      Fetching.answer(Fetching.tryUrl(http(), "arxiv", s"${cfg.arxiv.url}/pdf/$id"), s"arxiv has no PDF of $id")
    }
