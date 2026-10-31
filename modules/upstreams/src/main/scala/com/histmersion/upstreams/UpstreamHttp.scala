package com.histmersion.upstreams

import com.histmersion.plugin.PluginHttp
import io.circe.Json

import java.nio.charset.StandardCharsets.UTF_8

extension (http: PluginHttp)
  /** One GET answered as text (UTF-8). */
  def text(url: String, headers: Map[String, String] = Map.empty): Either[String, String] =
    http.get(url, headers).map(String(_, UTF_8))

  /** One GET answered as JSON; an answer that is none says so, with the URL. */
  def json(url: String, headers: Map[String, String] = Map.empty): Either[String, Json] =
    text(url, headers).flatMap(s =>
      io.circe.parser.parse(s).left.map(e => s"$url returned unparseable JSON: ${e.getMessage}")
    )
