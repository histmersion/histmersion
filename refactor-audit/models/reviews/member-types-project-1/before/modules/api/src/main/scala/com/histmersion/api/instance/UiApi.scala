package com.histmersion.api.instance
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.Api

object UiApi:

  import UiNotices.{Counters, SeenMark}

  lazy val counters: PublicEndpoint[Unit, Api.Error, Counters, Any] =
    endpoint.get.in("api" / "ui" / "counters").errorOut(jsonBody[Api.Error]).out(jsonBody[Counters])

  lazy val markSeen: PublicEndpoint[(String, SeenMark), Api.Error, Counters, Any] =
    endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(seenErrors)
      .out(jsonBody[Counters])

  private def seenErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        Api.Error.hasCode(Backup.CodeMaintenance)
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
