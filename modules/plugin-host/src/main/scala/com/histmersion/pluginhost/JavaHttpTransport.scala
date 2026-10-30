package com.histmersion.pluginhost

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Locale
import scala.jdk.CollectionConverters.*

/** The real client: `java.net.http`, following redirects, the URL used exactly as built (the provider URLs are already
  * percent-encoded).
  */
final class JavaHttpTransport(timeoutSeconds: => Int) extends HttpTransport: // by name: read per request
  private val client = HttpClient
    .newBuilder()
    .followRedirects(HttpClient.Redirect.NORMAL)
    .connectTimeout(Duration.ofSeconds(timeoutSeconds.toLong.max(1)))
    .version(HttpClient.Version.HTTP_1_1)
    .build()

  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
    scala.util
      .Try {
        val b = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(timeoutSeconds.toLong.max(1)))
        headers.foreach((k, v) => b.header(k, v))
        val resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray())
        val hs = resp
          .headers()
          .map()
          .asScala
          .toMap
          .flatMap((k, vs) => vs.asScala.headOption.map(v => k.toLowerCase(Locale.ROOT) -> v))
        HttpAnswer(resp.statusCode(), hs, resp.body())
      }
      .toEither
      .left
      .map(e => s"$url unreachable: ${e.getClass.getSimpleName}: ${e.getMessage}")
