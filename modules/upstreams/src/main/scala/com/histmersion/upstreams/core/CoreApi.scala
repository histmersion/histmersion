package com.histmersion
package upstreams.core
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.plugin.Header
import com.histmersion.plugin.UpstreamQuota
import io.circe.Json

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.domain.library.acquisition.CoreEndpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.landing.RepositoryLanding.Candidate
import com.histmersion.plugin.Plugin
import com.histmersion.upstreams.PageSize
import io.github.iltotore.iron.constraint.any.Pure

/** CORE's `search/works` with a key, through the server's CORE budget (`quota`) — the one way Histmersion calls CORE.
  * Asked once, never retried ([[Plugin.Http.fetch]]): the budget decides when CORE is asked again.
  */
final class CoreApi(core: CoreEndpoint, http: Plugin.Http, quota: UpstreamQuota):

  /** One search for `purpose` (`lookup`: a work's download URLs by its DOI; `search`: a saved query). */
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
          case Left(e)  => Left(CoreFailure.Failed(e.problem))
          case Right(a) =>
            quota.observe(a.status, a.headers)
            if a.status == Plugin.HttpResponse.Status(429) then
              Left(
                CoreFailure.Skipped(
                  Problem.Unclassified(
                    Legacy.Text(
                      quota.waitingUntil.fold("CORE answered HTTP 429")(u =>
                        s"CORE answered HTTP 429; not asked until $u"
                      )
                    )
                  )
                )
              )
            else if (a.status).value >= 400 then
              Left(
                CoreFailure.Failed(
                  Problem.Unclassified(Legacy.Text(s"$url: HTTP ${a.status} ${String(a.body.value.take(200), UTF_8)}"))
                )
              )
            else
              io.circe.parser
                .parse(String(a.body.value, UTF_8))
                .left
                .map(e =>
                  CoreFailure.Failed(
                    Problem.Unclassified(Legacy.Text(s"$url returned unparseable JSON: ${e.getMessage}"))
                  )
                )

object CoreApi:
  type SearchWorksKey = SearchWorksKey.T
  object SearchWorksKey extends Newtype[String, Pure]
  type SearchWorksQuery = SearchWorksQuery.T
  object SearchWorksQuery extends Newtype[String, Pure]
