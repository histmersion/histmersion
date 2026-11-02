package com.histmersion.plugins.events

import io.circe.Json
import com.histmersion.plugin.Plugin

/** One catalogue request answered as JSON, through the host's polite client: an answer that is no JSON says so, by the
  * catalogue's host, as the host's own client words it.
  */
final class CatalogueJson(http: Plugin.Http):
  def apply(url: String): Either[String, Json] =
    http
      .get(url)
      .flatMap(b =>
        io.circe.parser
          .parse(String(b, "UTF-8"))
          .left
          .map(e => s"${CatalogueJson.host(url)}: unparseable JSON (${e.getMessage})")
      )

object CatalogueJson:
  def host(url: String): String =
    scala.util.Try(java.net.URI.create(url).getHost).toOption.flatMap(Option(_)).getOrElse(url)
