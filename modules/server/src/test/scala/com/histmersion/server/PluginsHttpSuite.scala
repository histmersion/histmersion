package com.histmersion.server

import ox.supervised
import com.histmersion.core.AppConfig
import com.histmersion.plugin.PluginHttp
import com.histmersion.server.plugins.*
import org.ekrich.config.ConfigFactory

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters.*

/** `/api/about` and `/api/config` report the plugins the server started with — their state and their
  * settings, a secret as its REFERENCE and never its value. Skipped without HISTMERSION_TEST_DB_URL. */
class PluginsHttpSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)

  private def generation(): Generation =
    val got = scala.collection.mutable.Buffer.empty[String]
    val (found, _) = PluginHost.discover(getClass.getClassLoader)
    val user = ConfigFactory.parseString(
      """histmersion.plugins {
        |  maker.enabled = true
        |  consumer { enabled = true, token = "fake:portal", extra = ["value:hunter2", "env:PLUGINS_HTTP_TOKEN"] }
        |  typo { enabled = true }
        |}""".stripMargin)
    val env = ConfigFactory.parseMap(Map("PLUGINS_HTTP_TOKEN" -> "env-secret-value").asJava)
    val services = HostServices(() => new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
      env, (_, _, _) => ())
    val g = PluginHost.build(found ++ List(MakerPlugin(), ConsumerPlugin(got)), user, env, services)
    assertEquals(got.toList, List("v(portal)", "hunter2", "env-secret-value"))
    g

  test("about and config list the plugins; a secret shows as its reference, never as its value"):
    val base = AppConfig.load()
    val cfg  = base.copy(db = base.db.copy(url = testDb.get))
    supervised {
      val b = Main.server(cfg, "127.0.0.1", 0, OfflineConverter(), plugins = generation()).start()
      try
        val url = s"http://127.0.0.1:${b.port}"
        for path <- List("/api/about", "/api/config") do
          val (s, body) = get(url + path)
          assertEquals(s, 200, body)
          assert(body.contains("\"id\":\"keepassxc\"") && body.contains("\"state\":\"disabled\""), body)
          assert(body.contains("\"id\":\"consumer\"") && body.contains("\"state\":\"registered\""), body)
          assert(body.contains("\"provides\":[\"secret-vault:fake\"]"), body)
          assert(body.contains("fake:portal") && body.contains("env:PLUGINS_HTTP_TOKEN"), body)
          assert(body.contains("value:***"), body)
          assert(body.contains("histmersion.plugins.typo configures no plugin"), body)
          for leaked <- List("hunter2", "env-secret-value", "v(portal)") do
            assert(!body.contains(leaked), s"$path leaks a secret value: $body")
      finally b.stop()
    }
