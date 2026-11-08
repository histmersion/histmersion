package com.histmersion
package upstreams.http
import io.circe.Json

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.plugin.Plugin

extension (http: Plugin.Http)
  /** One GET answered as text (UTF-8). */
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String] =
    http
      .get(
        Plugin.Http.GetUrl(url),
        (headers).iterator.map { case (refactorKey, refactorItem) =>
          (Plugin.Http.GetHeadersPart1(refactorKey), Plugin.Http.GetHeadersPart2(refactorItem))
        }.toMap
      )
      .map(body => String(body.value, UTF_8))
      .left
      .map(_.value)

  /** One GET answered as JSON; an answer that is none says so, with the URL. */
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
    )
