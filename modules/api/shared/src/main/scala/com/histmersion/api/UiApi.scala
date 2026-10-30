package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The web UI's own plumbing ([[UiNotices]]): the toolbar's counts and "something new" marks. Kept next to [[Api]]
  * (which appends `UiApi.all`), like [[OpsApi]]. Definitions only.
  *
  * REST only, deliberately — like `GET /api/config`: these are the page's notification state, of no use to an agent or
  * a shell (which read the lists themselves with `queue`, `list_approvals` and `goals`), so there is no MCP tool or CLI
  * command for them.
  */
object UiApi:

  import UiNotices.{Counters, SeenMark}

  /** `GET /api/ui/counters` — every watched window's count, newest change, last-seen mark and whether something is new:
    * ONE request, one round trip to the store, for the page to poll.
    */
  lazy val counters: PublicEndpoint[Unit, ApiError, Counters, Any] =
    endpoint.get.in("api" / "ui" / "counters").errorOut(jsonBody[ApiError]).out(jsonBody[Counters])

  /** `POST /api/ui/seen/{board}` `{"at": latest}` — the reader opened that window; answers the counters as they are
    * now. An unknown board or a malformed instant is a 400. Exempt from the API key (it changes nothing but this mark —
    * see `AuthHandler`), so a reader without the key still clears what they have read.
    */
  lazy val markSeen: PublicEndpoint[(String, SeenMark), ApiError, Counters, Any] =
    endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(seenErrors)
      .out(jsonBody[Counters])

  /** 503 while a restore holds the maintenance lock (as every write), 400 for anything else. */
  private def seenErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(
        ApiError.hasCode(Backup.CodeMaintenance)
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** The path prefix of [[markSeen]], for the write gate's exemption. */
  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
