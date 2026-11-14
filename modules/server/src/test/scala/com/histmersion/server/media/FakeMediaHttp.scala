package com.histmersion
package server.media
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.domain.media.imports.{BuiltinMedia, PluginCollection}

import com.histmersion.server.ServerPlugins

import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.{HttpAnswer, HttpTransport, Polite, Registries}

import com.histmersion.config.{AppConfig, MediaConfig}
import com.histmersion.plugin.MediaCollection
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*

/** A provider that never opens a socket (#1446): answers come from `route`, every request is recorded with its headers,
  * and the waits a polite client would sleep are recorded instead.
  */
final class FakeMediaHttp(route: String => Either[String, HttpAnswer]) extends HttpTransport:
  val requests = scala.collection.mutable.ListBuffer.empty[(String, Map[String, String])]
  def get(
      url: HttpTransport.Url,
      headers: Map[CoreQuota.HeaderName, CoreQuota.HeaderValue]
  ): Either[Problem, HttpAnswer] =
    requests += url.value -> headers.map((k, v) => k.value -> v.value)
    route(url.value).left.map(m => Problem.Unclassified(Legacy.Text(m)))

object FakeMediaHttp:
  /** The answer a fake server gives: its status, its headers and body as they go over the wire. */
  private def answer(code: Int, headers: Map[String, String], body: Array[Byte]): Either[String, HttpAnswer] =
    Right(
      HttpAnswer(
        HttpAnswer.Code(code),
        headers.map((k, v) => CoreQuota.HeaderName(k) -> CoreQuota.HeaderValue(v)),
        HttpAnswer.Body(body)
      )
    )

  def json(body: String, headers: Map[String, String] = Map.empty, code: Int = 200): Either[String, HttpAnswer] =
    answer(code, Map("content-type" -> "application/json") ++ headers, body.getBytes("UTF-8"))

  def bytes(b: Array[Byte], mime: String): Either[String, HttpAnswer] =
    answer(200, Map("content-type" -> mime), b)

  def fixture(name: String): String = scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString

  /** The default media config with no pause between requests. */
  lazy val cfg: MediaConfig =
    AppConfig
      .load(org.ekrich.config.ConfigFactory.empty)
      .media
      .copy(requestIntervalMs = MediaConfig.RequestIntervalMs(0))

  /** A [[Polite]] over `http` whose sleeps are recorded, not slept, on a clock that sleeping advances. */
  def polite(
      http: HttpTransport,
      c: MediaConfig = cfg,
      refusalBackoffMs: List[Polite.RefusalBackoffMs] = Nil
  ): (Polite, scala.collection.mutable.ListBuffer[Polite.IntervalMs]) =
    val slept = scala.collection.mutable.ListBuffer.empty[Polite.IntervalMs]
    // `var`: the fake clock, advanced by every recorded sleep
    var now = Polite.EpochMs(1_000_000L)
    (
      new Polite(
        http,
        c,
        ms => { slept += ms; now = Polite.EpochMs(now.value + ms.value) },
        () => now,
        refusalBackoffMs
      ),
      slept
    )

  /** The first-party collection registered as `name`, as the host builds it: its plugin discovered on the class path
    * and supplied the host's settings `settings` (a `histmersion.conf` text: `histmersion.media.*`, and a keyed one's
    * `histmersion.plugins.<id>` block), its secret references read from `env`.
    */
  def collection(
      name: Registries.MediaCollectionsKey,
      settings: String = "",
      env: Map[String, String] = Map.empty
  ): MediaCollection =
    val g = ServerPlugins.offlineWith(Nil, ConfigFactory.parseString(settings), ConfigFactory.parseMap(env.asJava))
    g.registries.mediaCollections.getOrElse(
      name,
      throw IllegalStateException(s"no media collection '$name' registered: ${g.report}")
    )

  /** [[collection]] as the host's collection import runs one searched by a place's name: stored under its provider,
    * judged by its own allowlist in `c`.
    */
  def builtin(
      name: BuiltinMedia.EntryName,
      settings: String = "",
      env: Map[String, String] = Map.empty,
      c: MediaConfig = cfg
  ): PluginCollection =
    PluginCollection(
      BuiltinMedia.providerOf(name),
      collection(Registries.MediaCollectionsKey(name.value), settings, env),
      BuiltinMedia.licencesFor(name, c)
    )
