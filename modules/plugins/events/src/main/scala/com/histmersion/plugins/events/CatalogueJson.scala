package com.histmersion
package plugins.events
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure

/** One catalogue request answered as JSON, through the host's polite client: an answer that is no JSON says so, by the
  * catalogue's host, as the host's own client words it.
  */
final class CatalogueJson(http: Plugin.Http):
  def apply(url: Plugin.Http.Url): Either[Problem, Json] =
    http
      .get(url)
      .left
      .map(_.problem)
      .flatMap(b =>
        RawJson.parse(String(b.value, "UTF-8"))
          .left
          .map(e =>
            Problem.Unclassified(Legacy.Text(s"${CatalogueJson.host(url).value}: unparseable JSON ($e)"))
          )
      )

object CatalogueJson:
  type Host = Host.T
  object Host extends Newtype[String, Pure]

  def host(url: Plugin.Http.Url): Host =
    Host(scala.util.Try(java.net.URI.create(url.value).getHost).toOption.flatMap(Option(_)).getOrElse(url.value))
