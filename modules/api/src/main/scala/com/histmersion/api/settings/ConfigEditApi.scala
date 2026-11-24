package com.histmersion
package api.settings
import sttp.tapir.*
import com.histmersion.api.json.TapirJson.*
import sttp.model.StatusCode
import com.histmersion.api.given
import com.histmersion.api.Api
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@atBoundary(BoundaryKind.Http, "declares the Tapir endpoints of the config edit routes with their request and response bodies")
object ConfigEditApi:

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  lazy val get: PublicEndpoint[Option[ConfigEdit.Page.Selector], Api.Error, ConfigEdit.Page, Any] =
    (endpoint.get
      .in("api" / "settings")
      .in(query[Option[String]]("prefix"))
      .errorOut(Api.problemOut)
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
      .errorOut(Api.problemOut)
      .out(jsonBody[ConfigEdit.Written])

  lazy val restart: PublicEndpoint[Unit, Api.Error, ConfigEdit.Restart, Any] =
    endpoint.post.in("api" / "server" / "restart").errorOut(Api.problemOut).out(jsonBody[ConfigEdit.Restart])

  lazy val all: List[AnyEndpoint] = List(get, write, restart)
