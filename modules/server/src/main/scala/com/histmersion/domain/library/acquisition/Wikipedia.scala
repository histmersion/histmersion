package com.histmersion.domain.library.acquisition

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  def refs(articleUrl: String): Either[String, WikirefsResult] =
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
