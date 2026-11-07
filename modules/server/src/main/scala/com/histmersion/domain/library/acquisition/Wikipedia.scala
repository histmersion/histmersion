package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: com.histmersion.domain.library.acquisition.Wikipedia.ArticleUrl): Either[com.histmersion.domain.library.acquisition.Wikipedia.Problem, WikirefsResult] =
    AcquireParse
      .wikiPageTitle(articleUrl)
      .toRight(s"'$articleUrl' is not a Wikipedia article URL")
      .flatMap { page =>
        http
          .json(
            Url(
              cfg.wikipedia.url,
              "",
              "action" -> "parse",
              "page" -> page,
              "prop" -> "wikitext",
              "format" -> "json",
              "redirects" -> "1"
            )
          )
          .flatMap { j =>
            AcquireParse.wikiText(j).toRight(s"Wikipedia returned no wikitext for '$page'").map { text =>
              val (dois, arxiv) = AcquireParse.wikiRefs(text)
              WikirefsResult(AcquireParse.wikiTitle(j).getOrElse(page), articleUrl, dois.size, dois, arxiv)
            }
          }
      }

object Wikipedia:
  type ArticleUrl = ArticleUrl.T
  object ArticleUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
