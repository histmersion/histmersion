package com.histmersion
package upstreams.http
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.plugin.Header

import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.plugin.Plugin

extension (http: Plugin.Http)
  /** One GET answered as text (UTF-8). */
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String] =
    http
      .get(
        Plugin.Http.Url(url),
        (headers).iterator.map { case (refactorKey, refactorItem) =>
          (Header.Name(refactorKey), Header.Value(refactorItem))
        }.toMap
      )
      .map(body => String(body.value, UTF_8))
      .left
      .map(e => s"${e.problem.legacyText}")

  /** One GET answered as JSON; an answer that is none says so, with the URL. */
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json] =
    text(url, headers).flatMap(s =>
      RawJson.parse(s).left.map(e => s"$url returned unparseable JSON: $e")
    )
