package com.histmersion
package plugins.media

import com.histmersion.plugin.Header
import com.histmersion.plugin.Problem

import io.circe.Json

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.plugin.Plugin

extension (http: Plugin.Http)
  /** One GET through the host's polite client answered as JSON; an answer that is not JSON names its host. `headers` as
    * the client sends them — a key-gated API's key travels in one, never in the URL.
    */
  def askJson(
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value] = Map.empty
  ): Either[Plugin.Http.Failure, Json] =
    http
      .get(url, headers)
      .flatMap(body =>
        io.circe.parser
          .parse(String(body.value, UTF_8))
          .left
          .map(e =>
            Plugin.Http.Failure.Transport(
              Problem(
                s"${scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value)}: " +
                  s"unparseable JSON (${e.getMessage})"
              )
            )
          )
      )

extension [A](reading: Either[Problem, A])
  /** A reading of an answer that failed, as the failure of the request. */
  def unreadable: Either[Plugin.Http.Failure, A] = reading.left.map(Plugin.Http.Failure.Transport(_))

extension [A](readings: Either[Problem, List[Either[Problem, A]]])
  /** The records of an answer, each one unreadable on its own or not. */
  def unreadableEach: Either[Plugin.Http.Failure, List[Either[Plugin.Http.Failure, A]]] =
    readings.unreadable.map(_.map(_.unreadable))
