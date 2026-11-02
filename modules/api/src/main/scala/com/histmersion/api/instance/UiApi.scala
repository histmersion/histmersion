package com.histmersion.api.instance
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.ApiError
import com.histmersion.api.backup.Backup
import com.histmersion.api.given

object UiApi:

  import UiNotices.{Counters, SeenMark}

  lazy val counters: PublicEndpoint[Unit, ApiError, Counters, Any] =
    endpoint.get.in("api" / "ui" / "counters").errorOut(jsonBody[ApiError]).out(jsonBody[Counters])

  lazy val markSeen: PublicEndpoint[(String, SeenMark), ApiError, Counters, Any] =
    endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(seenErrors)
      .out(jsonBody[Counters])

  private def seenErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(
        ApiError.hasCode(Backup.CodeMaintenance)
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
