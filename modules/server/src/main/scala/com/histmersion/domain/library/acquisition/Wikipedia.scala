package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: WikiClient.ArticleUrl): Either[Problem, WikirefsResult] =
    AcquireParse
      .wikiPageTitle(Http.Url(articleUrl.value))
      .toRight(Problem.Unclassified(Legacy.Text(s"'$articleUrl' is not a Wikipedia article URL")))
      .flatMap { page =>
        http
          .json(
            Url(
              Http.Url(cfg.wikipedia.url.value),
              Url.UrlPath(""),
              "action" -> "parse",
              "page" -> page.value,
              "prop" -> "wikitext",
              "format" -> "json",
              "redirects" -> "1"
            )
          )
          .left
          .map(_.problem)
          .flatMap { j =>
            AcquireParse
              .wikiText(j)
              .toRight(Problem.Unclassified(Legacy.Text(s"Wikipedia returned no wikitext for '$page'")))
              .map { text =>
                val (dois, arxiv) = AcquireParse.wikiRefs(text)
                WikirefsResult(
                  WikirefsResult.Article(AcquireParse.wikiTitle(j).getOrElse(page).value),
                  WikirefsResult.Url(articleUrl.value),
                  WikirefsResult.DoiCount(dois.size),
                  dois.map(d => WikirefsResult.Dois(d.value)),
                  arxiv.map(a => WikirefsResult.Arxiv(a.value))
                )
              }
          }
      }
