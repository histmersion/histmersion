package com.histmersion
package plugins.media

import io.circe.Json

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.plugin.Plugin

extension (http: Plugin.Http)
  /** One GET through the host's polite client answered as JSON; an answer that is not JSON names its host. `headers` as
    * the client sends them — a key-gated API's key travels in one, never in the URL.
    */
  def askJson(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2] = Map.empty): Either[String, Json] =
    http
      .get(url, headers)
      .left
      .map(_.value)
      .flatMap(body =>
        io.circe.parser
          .parse(String(body.value, UTF_8))
          .left
          .map(e =>
            s"${scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value)}: " +
              s"unparseable JSON (${e.getMessage})"
          )
      )
