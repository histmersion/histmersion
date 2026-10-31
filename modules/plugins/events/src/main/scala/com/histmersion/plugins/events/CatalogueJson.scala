package com.histmersion.plugins.events

import com.histmersion.plugin.PluginHttp
import io.circe.Json

/** One catalogue request answered as JSON, through the host's polite client: an answer that is no JSON says so, by the
  * catalogue's host, as the host's own client words it.
  */
final class CatalogueJson(http: PluginHttp):
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
