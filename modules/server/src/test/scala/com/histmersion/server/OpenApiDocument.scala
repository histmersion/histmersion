package com.histmersion
package server

import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.histmersion.api.Api
import com.histmersion.api.json.TapirJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import hearth.kindlings.tapiropenapijsoniter.OpenApiJsoniter
import sttp.apispec.openapi.OpenAPI
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.internal.RichEndpointOutput

object OpenApiDocument:

  val options: OpenAPIDocsOptions = OpenAPIDocsOptions.default.copy(
    markOptionsAsNullable = true,
    defaultDecodeFailureOutput = input => OpenAPIDocsOptions.defaultDecodeFailureOutput(input).map(refused)
  )

  private def refused(text: EndpointOutput[?]): EndpointOutput[?] =
    val description = text.traverseOutputs { case b: EndpointIO.Body[?, ?] => b.info.description.toVector }
    statusCode(StatusCode.BadRequest).and(jsonBody[Api.Error].description(description.mkString(", ")))

  def of(endpoints: List[AnyEndpoint]): OpenAPI =
    OpenAPIDocsInterpreter(options).toOpenAPI(endpoints, "Histmersion", "contract")

  def json(endpoints: List[AnyEndpoint]): Json =
    val text = writeToString(of(endpoints))(using OpenApiJsoniter.openapi_3_1.openAPICodec)
    RawJson.parse(text).fold(e => throw AssertionError(e), identity).withoutNulls
