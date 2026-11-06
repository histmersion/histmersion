package com.histmersion.api.settings
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.given
import com.histmersion.api.Api

object ConfigEditApi:


  private def errors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])((Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(ConfigEdit.CodeChanged))).andThen(refactorResult => ((refactorResult) == (true)))),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error.hasCode(_root_.com.histmersion.api.Api.Error.Code(ConfigEdit.CodeUnavailable))).andThen(refactorResult4 => ((refactorResult4) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  lazy val get: PublicEndpoint[Option[com.histmersion.api.settings.ConfigEdit.Page.Selector], Api.Error, ConfigEdit.Page, Any] =
    (endpoint.get
      .in("api" / "settings")
      .in(query[Option[String]]("prefix"))
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Page])).mapIn[_root_.scala.Option[_root_.com.histmersion.api.settings.ConfigEdit.Page.Selector]]((refactorDecoded2: _root_.scala.Option[_root_.java.lang.String]) => (refactorDecoded2).map(refactorElement3 => _root_.com.histmersion.api.settings.ConfigEdit.Page.Selector(refactorElement3)))((refactorEncoded2: _root_.scala.Option[_root_.com.histmersion.api.settings.ConfigEdit.Page.Selector]) => (refactorEncoded2).map(refactorElement4 => (refactorElement4).value))

  lazy val write: PublicEndpoint[ConfigEdit.Write, Api.Error, ConfigEdit.Written, Any] =
    endpoint.post
      .in("api" / "settings")
      .in(jsonBody[ConfigEdit.Write])
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Written])

  lazy val restart: PublicEndpoint[Unit, Api.Error, ConfigEdit.Restart, Any] =
    endpoint.post.in("api" / "server" / "restart").errorOut(errors).out(jsonBody[ConfigEdit.Restart])

  lazy val all: List[AnyEndpoint] = List(get, write, restart)
