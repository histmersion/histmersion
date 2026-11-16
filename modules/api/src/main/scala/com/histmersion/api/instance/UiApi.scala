package com.histmersion
package api.instance
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.backup.Backup
import com.histmersion.api.given
import com.histmersion.api.Api

object UiApi:

  import UiNotices.{Counters, SeenMark}

  lazy val counters: PublicEndpoint[Unit, Api.Error, Counters, Any] =
    endpoint.get.in("api" / "ui" / "counters").errorOut(Api.problemOut).out(jsonBody[Counters])

  lazy val markSeen: PublicEndpoint[(UiNotices.Board.Wire, SeenMark), Api.Error, Counters, Any] =
    (endpoint.post
      .in("api" / "ui" / "seen" / path[String]("board"))
      .in(jsonBody[SeenMark])
      .errorOut(Api.problemOut)
      .out(jsonBody[Counters]))
      .mapIn[(UiNotices.Board.Wire, SeenMark)]((refactorDecoded: (String, SeenMark)) =>
        (UiNotices.Board.Wire((refactorDecoded)._1), (refactorDecoded)._2)
      )((refactorEncoded: (UiNotices.Board.Wire, SeenMark)) => (((refactorEncoded)._1).value, (refactorEncoded)._2))

  val SeenPathPrefix = "/api/ui/seen/"

  lazy val all: List[AnyEndpoint] = List(counters, markSeen)
