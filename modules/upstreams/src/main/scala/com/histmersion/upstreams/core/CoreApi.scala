package com.histmersion
package upstreams.core
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.Header
import com.histmersion.plugin.UpstreamQuota

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.domain.library.acquisition.CoreEndpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.landing.RepositoryLanding.Candidate
import com.histmersion.plugin.Plugin
import com.histmersion.upstreams.PageSize
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** CORE's `search/works` with a key, through the server's CORE budget (`quota`) — the one way Histmersion calls CORE.
  * Asked once, never retried ([[Plugin.Http.fetch]]): the budget decides when CORE is asked again.
  */
@atBoundary(BoundaryKind.Http, "calls CORE's works search and returns its JSON")
final class CoreApi(core: CoreEndpoint, http: Plugin.Http, quota: UpstreamQuota):

  /** One search for `purpose` (`lookup`: a work's download URLs by its DOI; `search`: a saved query). */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def searchWorks(
      key: CoreApi.SearchWorksKey,
      query: CoreApi.SearchWorksQuery,
      limit: PageSize,
      purpose: UpstreamQuota.TakePurpose
  ): Either[CoreFailure, Json] =
    val url =
      Url(Candidate.Url(core.url.value), Url.UrlPath("/search/works/"), "q" -> (query).value, "limit" -> limit.toString)
    quota.take(purpose) match
      case Left(why) => Left(CoreFailure.Skipped(why))
      case Right(_)  =>
        http.fetch(
          Plugin.Http.Url(url.value),
          Map {
            val refactorTuple = "Authorization" -> s"Bearer $key";
            (Header.Name(refactorTuple._1), Header.Value(refactorTuple._2))
          }
        ) match
          case Left(e)  => Left(CoreFailure.Failed(e))
          case Right(a) =>
            quota.observe(a.status, a.headers)
            val answered = Problem.Upstream.Http.here(
              Problem.Upstream.Name("core"),
              Problem.Upstream.Url(s"$url"),
              a.status,
              External.ReplyExcerpt.head(String(a.body.value, UTF_8))
            )
            if a.status == Problem.Upstream.Status(429) then Left(CoreFailure.Skipped(answered))
            else if (a.status).value >= 400 then Left(CoreFailure.Failed(answered))
            else
              RawJson
                .parse(String(a.body.value, UTF_8))
                .left
                .map(e =>
                  CoreFailure.Failed(
                    Problem.Upstream.BadReply.here(
                      Problem.Upstream.Name("core"),
                      Problem.Upstream.Url(s"$url"),
                      Problem.Upstream.Shape.Json,
                      External.Reported.of(s"$e")
                    )
                  )
                )

object CoreApi:
  type SearchWorksKey = SearchWorksKey.T
  object SearchWorksKey extends Newtype[String, Pure]
  type SearchWorksQuery = SearchWorksQuery.T
  object SearchWorksQuery extends Newtype[String, Pure]
