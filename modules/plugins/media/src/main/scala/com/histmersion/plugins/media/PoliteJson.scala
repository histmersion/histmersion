package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.Header

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
  ): Either[Problem, Json] =
    http
      .get(url, headers)
      .flatMap(body =>
        RawJson
          .parse(String(body.value, UTF_8))
          .left
          .map(e =>
            Problem.Upstream.BadReply.here(
              Problem.Upstream.Name(
                scala.util.Try(URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(s"$url")
              ),
              Problem.Upstream.Url(s"$url"),
              Problem.Upstream.Shape.Json,
              External.Reported.of(s"$e")
            )
          )
      )

extension [A](reading: Either[Problem, A])
  /** A reading of an answer that failed, as the failure of the request. */
  def unreadable: Either[Problem, A] = reading

extension [A](readings: Either[Problem, List[Either[Problem, A]]])
  /** The records of an answer, each one unreadable on its own or not. */
  def unreadableEach: Either[Problem, List[Either[Problem, A]]] =
    readings.unreadable.map(_.map(_.unreadable))
