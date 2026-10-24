package com.histmersion.pluginhost

import com.histmersion.domain.{HttpPolicy, Version}
import com.histmersion.plugin.*

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** A plugin's AI provider as the host calls it: its check and each model call guarded, so an exception
  * or a `null` from plugin code is the provider's failure, never the host's. */
object PluginAi:

  /** Whether the provider can serve `model` now, by its own check; an exception is a "no". */
  def check(provider: AiProvider, model: String): Either[String, Unit] =
    try Option(provider.check(model)).getOrElse(Left("its check answered null"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"its check threw ${e.getClass.getSimpleName}")

  /** One model call: the provider's answer, or its failure — an exception or a `null` being an error. */
  def turn(provider: AiProvider, request: AiTurnRequest, http: AiHttp): Either[AiFailure, AiTurn] =
    val answer =
      try Option(provider.turn(request, http)).getOrElse(Left(AiFailure.Error("the provider answered null")))
      catch
        case e: InterruptedException => throw e
        case NonFatal(e) => Left(AiFailure.Error(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}"))
    answer.flatMap(t => Option(t).toRight(AiFailure.Error("the provider answered null")))

/** The host's [[AiHttp]]: java.net.http over HTTP/1.1 (as every client of the server), the host's
  * User-Agent (no e-mail can reach it, [[HttpPolicy.userAgent]]), a timeout per call, and nothing sent
  * or received logged. */
final class HostAiHttp extends AiHttp:
  /** Built on the first call (an `HttpClient` starts a selector thread): a server that never calls a
    * plugin provider starts none. */
  private val built = java.util.concurrent.atomic.AtomicReference[java.net.http.HttpClient]()

  private def client: java.net.http.HttpClient =
    Option(built.get).getOrElse {
      val c = java.net.http.HttpClient.newBuilder()
        .version(java.net.http.HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()
      if built.compareAndSet(null, c) then c else built.get
    }

  private def userAgent = HttpPolicy.userAgent(s"Histmersion/${Version.version} (agents)")

  def post(url: String, headers: Map[String, String], body: String, timeoutSeconds: Int): Either[String, AiHttpResponse] =
    send(url, headers, timeoutSeconds, _.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)))

  def get(url: String, headers: Map[String, String], timeoutSeconds: Int): Either[String, AiHttpResponse] =
    send(url, headers, timeoutSeconds, _.GET())

  private def send(url: String, headers: Map[String, String], timeoutSeconds: Int,
      method: java.net.http.HttpRequest.Builder => java.net.http.HttpRequest.Builder): Either[String, AiHttpResponse] =
    try
      val b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
        .timeout(java.time.Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))
      headers.foreach((k, v) => if !k.equalsIgnoreCase("user-agent") then { val _ = b.header(k, v) })
      val _ = b.header("User-Agent", userAgent)
      val r = client.send(method(b).build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      Right(AiHttpResponse(r.statusCode, r.body,
        r.headers.map.asScala.toMap.collect { case (k, v) if !v.isEmpty => k.toLowerCase(java.util.Locale.ROOT) -> v.get(0) }))
    catch
      case e: InterruptedException => throw e
      // the message only: never the request, which may carry a key in a header
      case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))
