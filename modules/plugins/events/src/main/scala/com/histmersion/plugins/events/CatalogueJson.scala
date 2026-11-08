package com.histmersion
package plugins.events

import io.circe.Json
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure

/** One catalogue request answered as JSON, through the host's polite client: an answer that is no JSON says so, by the
  * catalogue's host, as the host's own client words it.
  */
final class CatalogueJson(http: Plugin.Http):
  def apply(url: Plugin.Http.GetUrl): Either[CatalogueJson.Problem, Json] =
    http
      .get(url)
      .left
      .map(e => CatalogueJson.Problem(e.value))
      .flatMap(b =>
        io.circe.parser
          .parse(String(b.value, "UTF-8"))
          .left
          .map(e => CatalogueJson.Problem(s"${CatalogueJson.host(url).value}: unparseable JSON (${e.getMessage})"))
      )

object CatalogueJson:
  type Host = Host.T
  object Host extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]

  def host(url: Plugin.Http.GetUrl): Host =
    Host(scala.util.Try(java.net.URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value))
