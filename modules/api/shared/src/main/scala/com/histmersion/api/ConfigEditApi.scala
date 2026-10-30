package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** Settings' endpoints ([[ConfigEdit]]), kept next to [[Api]] (which appends `ConfigEditApi.all`). Definitions
  * only. The same operations are the `config` tool's `get`, `set`, `unset` and `restart`, and `histmersion
  * config get|set|unset|restart`. */
object ConfigEditApi:

  /** 409 when the file changed since it was read, 503 when there is no file to write or no service to restart
    * the server, 400 for a change refused. */
  private def errors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(ConfigEdit.CodeChanged)),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(ApiError.hasCode(ConfigEdit.CodeUnavailable)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** `GET /api/settings?prefix=` — every setting (or those under `prefix`, `acquire` or `plugins.oa-core`) as the
    * server reads its settings file now: what the file says, what applies, the default, the documentation,
    * the environment variable, restart-only, and for a credential only whether it is set and how. */
  lazy val get: PublicEndpoint[Option[String], ApiError, ConfigEdit.Page, Any] =
    endpoint.get.in("api" / "settings").in(query[Option[String]]("prefix")).errorOut(errors).out(jsonBody[ConfigEdit.Page])

  /** `POST /api/settings` — change settings in the settings file (comments, order, includes and anything
    * written by hand kept; the old file kept as `.bak`), checked first, then reloaded into the running server. */
  lazy val write: PublicEndpoint[ConfigEdit.Write, ApiError, ConfigEdit.Written, Any] =
    endpoint.post.in("api" / "settings").in(jsonBody[ConfigEdit.Write]).errorOut(errors).out(jsonBody[ConfigEdit.Written])

  /** `POST /api/server/restart` — stop the server so that the service it runs under starts it again (settings
    * that only a restart applies). Refused where no such service runs it. */
  lazy val restart: PublicEndpoint[Unit, ApiError, ConfigEdit.Restart, Any] =
    endpoint.post.in("api" / "server" / "restart").errorOut(errors).out(jsonBody[ConfigEdit.Restart])

  lazy val all: List[AnyEndpoint] = List(get, write, restart)
