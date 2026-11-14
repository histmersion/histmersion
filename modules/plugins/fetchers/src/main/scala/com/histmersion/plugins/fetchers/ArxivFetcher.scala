package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.FetchedFile

/** The `arxiv` plugin's fetcher: an arXiv preprint's PDF, by its arXiv id. */
final class ArxivFetcher(cfg: UpstreamSettings, http: () => Plugin.Http) extends SourceFetcher:
  val access: Access = Access.Open
  def fetch(work: WorkKey, ctx: Fetch.Context): Fetch =
    work.arxiv.fold[Fetch](Fetch.Miss(Problem.Invalid.Missing.here(Problem.Field("arxiv")))) { id =>
      Downloads(http())
        .pdf(ctx.log, FetchedFile.Source("arxiv"), Plugin.Http.Url(s"${cfg.arxiv.url}/pdf/$id"))
        .fold[Fetch](
          Fetch.Miss(Problem.NotFound.Entity(Problem.TypeName("com.histmersion.plugin.Copy"), Problem.Raw(s"$id")))
        )(Fetch.Found(_))
    }
