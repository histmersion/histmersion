package com.histmersion.api.settings
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import com.histmersion.api.given
import com.histmersion.api.Api

object ConfigEditApi:

  type GetPart1 = GetPart1.T
  object GetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def errors =
    oneOf[Api.Error](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[Api.Error])(Api.Error.hasCode(ConfigEdit.CodeChanged)),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[Api.Error])(
        Api.Error.hasCode(ConfigEdit.CodeUnavailable)
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error]))
    )

  lazy val get: PublicEndpoint[Option[com.histmersion.api.settings.ConfigEditApi.GetPart1], Api.Error, ConfigEdit.Page, Any] =
    endpoint.get
      .in("api" / "settings")
      .in(query[Option[String]]("prefix"))
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Page])

  lazy val write: PublicEndpoint[ConfigEdit.Write, Api.Error, ConfigEdit.Written, Any] =
    endpoint.post
      .in("api" / "settings")
      .in(jsonBody[ConfigEdit.Write])
      .errorOut(errors)
      .out(jsonBody[ConfigEdit.Written])

  lazy val restart: PublicEndpoint[Unit, Api.Error, ConfigEdit.Restart, Any] =
    endpoint.post.in("api" / "server" / "restart").errorOut(errors).out(jsonBody[ConfigEdit.Restart])

  lazy val all: List[AnyEndpoint] = List(get, write, restart)
