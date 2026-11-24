package com.histmersion
package upstreams.http
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.plugin.Header

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.plugin.Plugin
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

extension (http: Plugin.Http)
  /** One GET answered as text (UTF-8). */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @atBoundary(BoundaryKind.Http, "reads a plugin HTTP answer as text or JSON")
  def text(url: String, headers: Map[String, String] = Map.empty): Either[Problem, String] =
    http
      .get(
        Plugin.Http.Url(url),
        (headers).iterator.map { case (refactorKey, refactorItem) =>
          (Header.Name(refactorKey), Header.Value(refactorItem))
        }.toMap
      )
      .map(body => String(body.value, UTF_8))

  /** One GET answered as JSON; an answer that is none says so, with the URL. */
  def json(url: String, headers: Map[String, String] = Map.empty): Either[Problem, Json] =
    text(url, headers).flatMap(s =>
      RawJson
        .parse(s)
        .left
        .map(e =>
          Problem.Upstream.BadReply.here(
            Problem.Upstream
              .Name(s"${scala.util.Try(URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)}"),
            Problem.Upstream.Url(s"$url"),
            Problem.Upstream.Shape.Json,
            External.Reported.of(s"$e")
          )
        )
    )
