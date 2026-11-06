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

  lazy val markSeen: PublicEndpoint[(com.histmersion.api.instance.UiNotices.Board.Wire, SeenMark), Api.Error, Counters, Any] =
    (endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(seenErrors)
      .out(jsonBody[Counters])).mapIn[(_root_.com.histmersion.api.instance.UiNotices.Board.Wire, _root_.com.histmersion.api.instance.UiNotices.SeenMark)]((refactorDecoded: (_root_.java.lang.String, _root_.com.histmersion.api.instance.UiNotices.SeenMark)) => (_root_.com.histmersion.api.instance.UiNotices.Board.Wire((refactorDecoded)._1), (refactorDecoded)._2))((refactorEncoded: (_root_.com.histmersion.api.instance.UiNotices.Board.Wire, _root_.com.histmersion.api.instance.UiNotices.SeenMark)) => (((refactorEncoded)._1).value, (refactorEncoded)._2))

  private def seenErrors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(Backup.CodeMaintenance))).andThen(refactorResult2 => ((refactorResult2) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
