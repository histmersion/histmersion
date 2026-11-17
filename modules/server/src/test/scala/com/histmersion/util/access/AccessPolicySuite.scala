package com.histmersion
package util.access

import java.net.InetAddress

import scala.io.Source

import hearth.kindlings.optics.syntax.*
import org.ekrich.config.ConfigFactory
import ox.supervised

import com.histmersion.config.{AppConfig, AuthConfig, ServerConfig}
import com.histmersion.server.Composition
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentence

/** The `auth` settings read into a policy, and the start-up rule: a server whose bind address can be reached from a
  * network its settings leave open does not start. Pure but for the last test, which builds a server that must refuse
  * before it opens anything (no database).
  */
class AccessPolicySuite extends munit.FunSuite:

  private val defaults = AppConfig.load(ConfigFactory.empty)

  private def settings(text: String): AppConfig =
    AppConfig.loadWithUserText(ConfigFactory.empty, text).fold(e => fail(s"$e"), _.config)

  private def policy(cfg: AppConfig): AccessPolicy =
    AccessPolicy.from(cfg.auth, cfg.server).fold(e => fail(s"$e"), identity)

  private def at(text: String): InetAddress = Cidr.literal(text).getOrElse(fail(text))

  private def auth(
      key: String = "",
      trusted: List[String] = List("127.0.0.0/8", "::1/128"),
      authenticated: List[String] = Nil
  ): AuthConfig =
    defaults.auth
      .modify(_.apiKey)
      .setTo(AuthConfig.ApiKey(key))
      .modify(_.trustedNetworks)
      .setTo(trusted.map(AuthConfig.Network(_)))
      .modify(_.authenticatedNetworks)
      .setTo(authenticated.map(AuthConfig.Network(_)))

  private def bound(host: String): ServerConfig = defaults.server.modify(_.host).setTo(ServerConfig.Host(host))

  private def listener(a: AuthConfig, host: String): Either[Problems, AccessPolicy] =
    AccessPolicy.forListener(a, bound(host))

  private def exposed(bind: String, reach: String, why: Problem.Config.Exposure): Either[Problems, AccessPolicy] =
    Left(Problem.Config.Exposed(Problem.Raw(bind), Problem.Raw(reach), why))

  // ── the settings ───────────────────────────────────────────────────────────────────────────────

  test("the defaults: bound to 127.0.0.1, loopback trusted as the owner, no other network accepted"):
    assertEquals(s"${defaults.server.host}", "127.0.0.1")
    val p = policy(defaults)
    assertEquals(p.trustedAs, AccessPolicy.Principal.Owner)
    assertEquals(p.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("::1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("192.168.1.20")), None)
    assertEquals(p.requirementOf(at("100.101.102.103")), None)
    assert(AccessPolicy.forListener(defaults.auth, defaults.server).isRight)

  test("the tailnet example of reference.conf, as written there, trusts the tailnet's devices"):
    val reference = Source.fromResource("reference.conf").getLines().toList
    val example = reference
      .find(l => l.trim.startsWith("# trusted-networks = [") && l.contains("100.64.0.0/10"))
      .getOrElse(fail("reference.conf has no commented tailnet example"))
    val line = example.trim.stripPrefix("#").trim.takeWhile(_ != '#').trim
    val p = policy(settings(s"histmersion.auth { $line }"))
    assertEquals(p.requirementOf(at("100.101.102.103")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("fd7a:115c:a1e0::5")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("192.168.1.20")), None)

  test("the three profiles of the docs read and start"):
    val local = settings("")
    val tailscale = settings(
      """histmersion {
        |  server.host = "0.0.0.0"
        |  auth {
        |    api-key = "k3y"
        |    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10", "fd7a:115c:a1e0::/48"]
        |    authenticated-networks = ["0.0.0.0/0", "::/0"]
        |    hosts = ["mymac.tail1234.ts.net", "100.101.102.103"]
        |  }
        |}""".stripMargin
    )
    val institution = settings(
      """histmersion {
        |  server { host = "0.0.0.0", public-url = "https://histmersion.example.edu" }
        |  auth {
        |    api-key = "k3y"
        |    trusted-networks = []
        |    authenticated-networks = ["0.0.0.0/0", "::/0"]
        |    hosts = ["histmersion.example.edu"]
        |  }
        |}""".stripMargin
    )
    List(local, tailscale, institution).foreach(c => assert(AccessPolicy.forListener(c.auth, c.server).isRight, c.auth))
    val t = policy(tailscale)
    // the longest prefix decides: loopback and the tailnet are trusted inside the authenticated everything
    assertEquals(t.requirementOf(at("100.101.102.103")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(t.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(t.requirementOf(at("192.168.1.20")), Some(AccessPolicy.Requirement.Authenticated))
    assert(t.answersTo(AccessPolicy.HostName.ofHeader("MyMac.tail1234.ts.net:8000")))
    val i = policy(institution)
    assertEquals(i.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Authenticated))
    assert(i.answersTo(AccessPolicy.HostName.ofHeader("histmersion.example.edu")))
    assert(
      i.acceptsChangesFrom(
        AccessPolicy.Origin.parse("https://histmersion.example.edu").get,
        AccessPolicy.Port(8000)
      ),
      "the public URL's origin, whatever port the server itself listens on behind its proxy"
    )

  test("every wrong setting is named, not just the first"):
    val wrong =
      auth().modify(_.trustedNetworks).setTo(List(AuthConfig.Network("127.0.0.0/8"), AuthConfig.Network("lan")))
    val all = wrong
      .modify(_.authenticatedNetworks)
      .setTo(List(AuthConfig.Network("10.0.0.0/33")))
      .modify(_.trustedAs)
      .setTo(AuthConfig.TrustedAs("root"))
      .modify(_.origins)
      .setTo(List(AuthConfig.Origin("ftp://x")))
    assertEquals(
      AccessPolicy.from(all, defaults.server).left.map(_.toVector),
      Left(
        Vector(
          Problem.Config
            .Setting(Problem.SettingKey("auth.trusted-networks"), Problem.Config.Fault.Malformed(Problem.Raw("lan"))),
          Problem.Config.Setting(
            Problem.SettingKey("auth.authenticated-networks"),
            Problem.Config.Fault.Malformed(Problem.Raw("10.0.0.0/33"))
          ),
          Problem.Config
            .Setting(Problem.SettingKey("auth.origins"), Problem.Config.Fault.Malformed(Problem.Raw("ftp://x"))),
          Problem.Config
            .Setting(Problem.SettingKey("auth.trusted-as"), Problem.Config.Fault.Unknown(Problem.Raw("root")))
        )
      )
    )

  test("one network listed as both trusted and authenticated contradicts itself"):
    assertEquals(
      AccessPolicy.from(auth(authenticated = List("127.0.0.0/8")), defaults.server).left.map(_.toVector),
      Left(
        Vector(
          Problem.Config.Setting(
            Problem.SettingKey("auth.authenticated-networks"),
            Problem.Config.Fault.Conflicts(Problem.SettingKey("auth.trusted-networks"))
          )
        )
      )
    )

  test("the names a request may give: loopback's, the bind's, the public URL's and the listed ones"):
    val p = policy(
      settings("""histmersion { server.host = "192.168.1.5", auth.hosts = ["Archive.Example.org."] }""")
    )
    List("localhost:8000", "127.0.0.1", "[::1]:8000", "192.168.1.5:8000", "archive.example.org", "LOCALHOST")
      .foreach(h => assert(p.answersTo(AccessPolicy.HostName.ofHeader(h)), h))
    List("rebound.example", "127.0.0.2", "localhost.evil.example", "[::2]")
      .foreach(h => assert(!p.answersTo(AccessPolicy.HostName.ofHeader(h)), h))
    // a wildcard bind adds no name
    assert(!policy(settings("""histmersion.server.host = "0.0.0.0"""")).answersTo(AccessPolicy.HostName.of("0.0.0.0")))

  // ── the start-up rule ──────────────────────────────────────────────────────────────────────────

  test("bound to loopback, the default networks start, with a key or without"):
    assert(listener(auth(), "127.0.0.1").isRight)
    assert(listener(auth(key = "k"), "127.0.0.1").isRight)
    assert(listener(auth(), "::1").isRight)
    assert(listener(auth(), "localhost").isRight)

  test("bound beyond loopback with an empty key and only loopback trusted: refused, naming the settings"):
    val refused = listener(auth(), "0.0.0.0")
    assertEquals(refused, exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted))
    val said = refused.left.toOption.get.toVector.head.sentence
    List("server.host", "auth.trusted-networks", "auth.authenticated-networks", "HISTMERSION_BIND")
      .foreach(k => assert(said.contains(k), said))

  test("an authenticated network with no key leaves it open: refused until the key is set"):
    assertEquals(
      listener(auth(authenticated = List("0.0.0.0/0")), "0.0.0.0"),
      exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.NoKey)
    )
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "0.0.0.0").isRight)

  test("a network trusted as a whole needs no key; part of the reach left unlisted is still refused"):
    assert(listener(auth(trusted = List("0.0.0.0/0")), "0.0.0.0").isRight)
    assertEquals(
      listener(auth(trusted = List("0.0.0.0/1"), authenticated = Nil), "0.0.0.0"),
      exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted)
    )
    assert(
      listener(auth(key = "k", trusted = List("0.0.0.0/1"), authenticated = List("128.0.0.0/1")), "0.0.0.0").isRight
    )

  test("an address is reached from its whole family; the dual-stack wildcard and a name from both"):
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "192.168.1.5").isRight)
    assertEquals(
      listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "::"),
      exposed("::", "::/0", Problem.Config.Exposure.Unlisted)
    )
    assertEquals(
      listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "myhost.example"),
      exposed("myhost.example", "::/0", Problem.Config.Exposure.Unlisted)
    )
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0", "::/0")), "::").isRight)
    // ::1 alone is reached from IPv6 loopback only
    assertEquals(
      listener(auth(trusted = List("127.0.0.0/8")), "::1"),
      exposed("::1", "::1/128", Problem.Config.Exposure.Unlisted)
    )

  test("the server refuses to start before it opens anything"):
    val open = defaults.modify(_.auth).setTo(auth())
    val thrown = intercept[IllegalStateException] {
      supervised(Composition.server(open, ServerConfig.Host("0.0.0.0"), ServerConfig.Port(0)))
    }
    assert(thrown.getMessage.contains("server.host = '0.0.0.0' can be reached from 0.0.0.0/0"), thrown.getMessage)
