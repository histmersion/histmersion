package com.histmersion
package pluginhost

import com.histmersion.util.problem.{External, Problem}
import com.histmersion.plugin.*
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory

import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*

class PluginEnvironmentSuite extends munit.FunSuite:

  final private case class Hosts(hosts: List[String]) derives ConfigReader

  final private class Listing extends Plugin:
    type Config = Hosts
    val info =
      Plugin.Info(Plugin.Info.Id("zq-listed"), Plugin.Info.Version("1.0.0"), Plugin.Info.SpiVersion(Spi.Version))
    val received = AtomicReference(Option.empty[Hosts])
    override def defaultsResource = Plugin.DefaultsResource("zq-listed-defaults.conf")
    def register(c: Hosts, ctx: Plugin.Context) =
      received.set(Some(c))
      Right(())

  private val services =
    HostServices(
      () =>
        new Plugin.Http {
          def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
            Left(
              Problem.Upstream.Unreachable.here(
                Problem.Upstream.Name("x"),
                Problem.Upstream.Url("https://x.example"),
                External.Reported("offline")
              )
            )
        },
      ConfigFactory.empty,
      (_, _, _) => (),
      _ => UpstreamQuota.Unlimited
    )

  private def hostsGiven(user: String, env: Map[String, String]): (Generation, Option[List[String]]) =
    val plugin = Listing()
    val generation =
      PluginHost.build(List(plugin), ConfigFactory.parseString(user), ConfigFactory.parseMap(env.asJava), services)
    (generation, plugin.received.get.map(_.hosts))

  test("a list setting a variable the process itself has set is checked and read against the given environment"):
    val (filed, filedHosts) = hostsGiven("""histmersion.plugins.zq-listed.hosts = ["a.example", "b.example"]""", Map())
    assert(filed.report.ok, filed.report.toString)
    assertEquals(filedHosts, Some(List("a.example", "b.example")))
    val (defaulted, defaultHosts) = hostsGiven("", Map())
    assert(defaulted.report.ok, defaulted.report.toString)
    assertEquals(defaultHosts, Some(List("default.example")))
    val (overridden, overriddenHosts) = hostsGiven("", Map("HOME" -> "c.example, d.example"))
    assert(overridden.report.ok, overridden.report.toString)
    assertEquals(overriddenHosts, Some(List("c.example", "d.example")))
