package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: com.histmersion.domain.library.acquisition.Wikipedia.RefsArticleUrl): Either[com.histmersion.domain.library.acquisition.Wikipedia.RefsResultPart1, WikirefsResult] =
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
  type RefsResultPart1 = RefsResultPart1.T
  object RefsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsArticleUrl = RefsArticleUrl.T
  object RefsArticleUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
