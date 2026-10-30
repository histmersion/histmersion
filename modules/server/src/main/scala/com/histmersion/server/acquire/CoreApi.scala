package com.histmersion.server.acquire

import com.histmersion.config.AcquireConfig
import com.histmersion.domain.CoreQuota
import com.histmersion.pluginhost.Url

import io.circe.Json

/** CORE's `search/works` with a key, through the shared [[CoreBudget]] — the one way Histmersion calls CORE. Asked
  * once, never retried: the budget decides when CORE is asked again.
  */
final class CoreApi(cfg: AcquireConfig, http: Http, budget: CoreBudget):

  def limits: CoreQuota.Limits = CoreBudget.limits(cfg.core)

  /** Why `purpose` would not be asked now (CORE's block, the day's budget), if it would not. */
  def unavailable(purpose: CoreQuota.Purpose): Option[String] = budget.unavailable(limits, purpose).map(_.describe)

  def searchWorks(key: String, query: String, limit: Int, purpose: CoreQuota.Purpose): Either[CoreFailure, Json] =
    val url = Url(cfg.core.url, "/search/works/", "q" -> query, "limit" -> limit.toString)
    budget.call(limits, purpose)(http.once(url, Map("Authorization" -> s"Bearer $key"))) match
      case Left(refusal)                    => Left(CoreFailure.Skipped(refusal.describe))
      case Right(Left(e))                   => Left(CoreFailure.Failed(e))
      case Right(Right(a)) if a.code == 429 =>
        Left(
          CoreFailure.Skipped(
            budget.state.block.fold("CORE answered HTTP 429")(b =>
              s"CORE answered HTTP 429; not asked until ${b.until}"
            )
          )
        )
      case Right(Right(a)) if a.code >= 400 =>
        Left(CoreFailure.Failed(s"$url: HTTP ${a.code} ${new String(a.body.take(200), "UTF-8")}"))
      case Right(Right(a)) =>
        io.circe.parser
          .parse(new String(a.body, "UTF-8"))
          .left
          .map(e => CoreFailure.Failed(s"$url returned unparseable JSON: ${e.getMessage}"))
