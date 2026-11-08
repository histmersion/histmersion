package com.histmersion
package api.instance
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

  lazy val markSeen: PublicEndpoint[(UiNotices.Board.Wire, SeenMark), Api.Error, Counters, Any] =
    (endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(seenErrors)
      .out(jsonBody[Counters]))
      .mapIn[(UiNotices.Board.Wire, SeenMark)]((refactorDecoded: (String, SeenMark)) =>
        (UiNotices.Board.Wire((refactorDecoded)._1), (refactorDecoded)._2)
      )((refactorEncoded: (UiNotices.Board.Wire, SeenMark)) => (((refactorEncoded)._1).value, (refactorEncoded)._2))

  private def seenErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error
          .hasCode(Api.Error.Code(Backup.CodeMaintenance)))
          .andThen(refactorResult2 => ((refactorResult2) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
