package com.histmersion
package api.settings
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.model.StatusCode
import com.histmersion.api.given
import com.histmersion.api.Api

object ConfigEditApi:

  private def errors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])(
        (Api.Error
          .hasCode(Api.Error.Code(ConfigEdit.CodeChanged)))
          .andThen(refactorResult => ((refactorResult) == (true)))
      ),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        (Api.Error
          .hasCode(Api.Error.Code(ConfigEdit.CodeUnavailable)))
          .andThen(refactorResult4 => ((refactorResult4) == (true)))
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  lazy val get: PublicEndpoint[Option[ConfigEdit.Page.Selector], Api.Error, ConfigEdit.Page, Any] =
    (endpoint.get
      .in("api" / "settings")
      .in(query[Option[String]]("prefix"))
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Page]))
      .mapIn[Option[ConfigEdit.Page.Selector]]((refactorDecoded2: Option[String]) =>
        (refactorDecoded2).map(refactorElement3 => ConfigEdit.Page.Selector(refactorElement3))
      )((refactorEncoded2: Option[ConfigEdit.Page.Selector]) =>
        (refactorEncoded2).map(refactorElement4 => (refactorElement4).value)
      )

  lazy val write: PublicEndpoint[ConfigEdit.Write, Api.Error, ConfigEdit.Written, Any] =
    endpoint.post
      .in("api" / "settings")
      .in(jsonBody[ConfigEdit.Write])
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Written])

  lazy val restart: PublicEndpoint[Unit, Api.Error, ConfigEdit.Restart, Any] =
    endpoint.post.in("api" / "server" / "restart").errorOut(errors).out(jsonBody[ConfigEdit.Restart])

  lazy val all: List[AnyEndpoint] = List(get, write, restart)
