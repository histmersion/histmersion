package com.histmersion.plugins.events

import io.circe.Json
import com.histmersion.plugin.Plugin

/** One catalogue request answered as JSON, through the host's polite client: an answer that is no JSON says so, by the
  * catalogue's host, as the host's own client words it.
  */
final class CatalogueJson(http: Plugin.Http):
  def apply(url: com.histmersion.plugin.Plugin.Http.GetUrl): Either[com.histmersion.plugins.events.CatalogueJson.Problem, Json] =
    http
      .get(url)
      .flatMap(b =>
        io.circe.parser
          .parse(String(b, "UTF-8"))
          .left
          .map(e => s"${CatalogueJson.host(url)}: unparseable JSON (${e.getMessage})")
      )

object CatalogueJson:
  type Host = Host.T
  object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def host(url: com.histmersion.plugin.Plugin.Http.GetUrl): com.histmersion.plugins.events.CatalogueJson.Host =
    scala.util.Try(java.net.URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)
