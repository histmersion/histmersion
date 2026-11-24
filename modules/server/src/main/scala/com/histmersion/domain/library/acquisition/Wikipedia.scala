package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem
import com.histmersion.plugin.WorkKey

import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.http.Url

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.WikirefsResult
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class Wikipedia(cfg: AcquireConfig, http: Http) extends WikiClient:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def refs(articleUrl: WikiClient.ArticleUrl): Either[Problem, WikirefsResult] =
    AcquireParse
      .wikiPageTitle(Http.Url(articleUrl.value))
      .toRight(
        Problem.Invalid.Unreadable.here(
          Problem.TypeName.of(WikiClient.ArticleUrl),
          Problem.Raw.of(articleUrl),
          Vector(Problem.Raw("https://en.wikipedia.org/wiki/Bronze_Age_collapse"))
        )
      )
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
          .flatMap { j =>
            AcquireParse
              .wikiText(j)
              .toRight(Problem.NotFound.Entity(Problem.TypeName.of(WorkKey.Title), Problem.Raw.of(page)))
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
