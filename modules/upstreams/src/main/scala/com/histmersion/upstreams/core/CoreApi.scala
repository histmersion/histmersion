package com.histmersion.upstreams.core
import com.histmersion.plugin.UpstreamQuota
import io.circe.Json

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.domain.library.acquisition.CoreEndpoint
import com.histmersion.upstreams.http.Url
import com.histmersion.plugin.Plugin

/** CORE's `search/works` with a key, through the server's CORE budget (`quota`) — the one way Histmersion calls CORE.
  * Asked once, never retried ([[Plugin.Http.fetch]]): the budget decides when CORE is asked again.
  */
final class CoreApi(core: CoreEndpoint, http: Plugin.Http, quota: UpstreamQuota):

  /** One search for `purpose` (`lookup`: a work's download URLs by its DOI; `search`: a saved query). */
  def searchWorks(key: String, query: String, limit: Int, purpose: String): Either[CoreFailure, Json] =
    val url = Url(core.url, "/search/works/", "q" -> query, "limit" -> limit.toString)
    quota.take(purpose) match
      case Left(why) => Left(CoreFailure.Skipped(why))
      case Right(_)  =>
        http.fetch(url, Map("Authorization" -> s"Bearer $key")) match
          case Left(e)  => Left(CoreFailure.Failed(e))
          case Right(a) =>
            quota.observe(a.status, a.headers)
            if a.status == 429 then
              Left(
                CoreFailure.Skipped(
                  quota.waitingUntil.fold("CORE answered HTTP 429")(u => s"CORE answered HTTP 429; not asked until $u")
                )
              )
            else if a.status >= 400 then
              Left(CoreFailure.Failed(s"$url: HTTP ${a.status} ${String(a.body.take(200), UTF_8)}"))
            else
              io.circe.parser
                .parse(String(a.body, UTF_8))
                .left
                .map(e => CoreFailure.Failed(s"$url returned unparseable JSON: ${e.getMessage}"))
