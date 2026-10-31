package com.histmersion.plugins.media

import com.histmersion.plugin.PluginHttp
import io.circe.Json

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

extension (http: PluginHttp)
  /** One GET through the host's polite client answered as JSON; an answer that is not JSON names its host. `headers` as
    * the client sends them — a key-gated API's key travels in one, never in the URL.
    */
  def askJson(url: String, headers: Map[String, String] = Map.empty): Either[String, Json] =
    http
      .get(url, headers)
      .flatMap(body =>
        io.circe.parser
          .parse(String(body, UTF_8))
          .left
          .map(e =>
            s"${scala.util.Try(URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)}: " +
              s"unparseable JSON (${e.getMessage})"
          )
      )
