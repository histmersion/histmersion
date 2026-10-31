package com.histmersion.server.media

import com.histmersion.server.ServerPlugins

import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Polite}

import com.histmersion.config.{AppConfig, MediaConfig}
import com.histmersion.plugin.MediaCollection
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*

/** A provider that never opens a socket (#1446): answers come from `route`, every request is recorded with its headers,
  * and the waits a polite client would sleep are recorded instead.
  */
final class FakeMediaHttp(route: String => Either[String, HttpAnswer]) extends HttpTransport:
  val requests = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
  def get(url: String, headers: Map[String, String]): Either[String, HttpAnswer] =
    requests += url -> headers
    route(url)

object FakeMediaHttp:
  def json(body: String, headers: Map[String, String] = Map.empty, code: Int = 200): Either[String, HttpAnswer] =
    Right(HttpAnswer(code, Map("content-type" -> "application/json") ++ headers, body.getBytes("UTF-8")))

  def bytes(b: Array[Byte], mime: String): Either[String, HttpAnswer] =
    Right(HttpAnswer(200, Map("content-type" -> mime), b))

  def fixture(name: String): String = scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString

  /** The default media config with no pause between requests. */
  lazy val cfg: MediaConfig =
    AppConfig.load(org.ekrich.config.ConfigFactory.empty).media.copy(requestIntervalMs = 0)

  /** A [[Polite]] over `http` whose sleeps are recorded, not slept, on a clock that sleeping advances. */
  def polite(
      http: HttpTransport,
      c: MediaConfig = cfg,
      refusalBackoffMs: List[Long] = Nil
  ): (Polite, scala.collection.mutable.ListBuffer[Long]) =
    val slept = scala.collection.mutable.ListBuffer.empty[Long]
    // `var`: the fake clock, advanced by every recorded sleep
    var now = 1_000_000L
    (Polite(http, c, ms => { slept += ms; now += ms }, () => now, refusalBackoffMs), slept)

  /** The first-party collection registered as `name`, as the host builds it: its plugin discovered on the class path
    * and supplied the host's settings `settings` (a `histmersion.conf` text: `histmersion.media.*`, and a keyed one's
    * `histmersion.plugins.<id>` block), its secret references read from `env`.
    */
  def collection(name: String, settings: String = "", env: Map[String, String] = Map.empty): MediaCollection =
    val g = ServerPlugins.offlineWith(Nil, ConfigFactory.parseString(settings), ConfigFactory.parseMap(env.asJava))
    g.registries.mediaCollections.getOrElse(
      name,
      throw IllegalStateException(s"no media collection '$name' registered: ${g.report}")
    )

  /** [[collection]] as the host's collection import runs one searched by a place's name: stored under its provider,
    * judged by its own allowlist in `c`.
    */
  def builtin(
      name: String,
      settings: String = "",
      env: Map[String, String] = Map.empty,
      c: MediaConfig = cfg
  ): PluginCollection =
    PluginCollection(BuiltinMedia.providerOf(name), collection(name, settings, env), BuiltinMedia.licencesFor(name, c))
