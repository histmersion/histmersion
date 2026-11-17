package com.histmersion
package util.access

import java.net.InetAddress

import scala.io.Source

import hearth.kindlings.optics.syntax.*
import org.ekrich.config.ConfigFactory
import ox.supervised

import com.histmersion.config.{AppConfig, AuthConfig, ServerConfig}
import com.histmersion.server.Composition
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentences

/** The `auth` settings read into a policy, and the start-up rule: a server one of whose bind addresses can be reached
  * from a network its settings leave open does not start, nor one whose HTTPS public URL sits in front of a trusted
  * loopback with no proxy declared. Pure but for the last test, which builds a server that must refuse before it opens
  * anything (no database).
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
      authenticated: List[String] = Nil,
      refused: List[String] = Nil,
      proxy: String = "none"
  ): AuthConfig =
    defaults.auth
      .modify(_.apiKey)
      .setTo(AuthConfig.ApiKey(key))
      .modify(_.trustedNetworks)
      .setTo(trusted.map(AuthConfig.Network(_)))
      .modify(_.authenticatedNetworks)
      .setTo(authenticated.map(AuthConfig.Network(_)))
      .modify(_.refusedNetworks)
      .setTo(refused.map(AuthConfig.Network(_)))
      .modify(_.proxy)
      .setTo(AuthConfig.Proxy(proxy))

  private def binds(first: String, more: String*): NonEmptyVector[ServerConfig.Host] =
    NonEmptyVector.of(ServerConfig.Host(first), more.map(ServerConfig.Host(_))*)

  private def bound(first: String, more: String*): ServerConfig =
    defaults.server.modify(_.host).setTo(binds(first, more*))

  private def listener(a: AuthConfig, first: String, more: String*): Either[Problems, AccessPolicy] =
    AccessPolicy.forListener(a, bound(first, more*))

  private def exposed(bind: String, reach: String, why: Problem.Config.Exposure): Problem =
    Problem.Config.Exposed(Problem.Raw(bind), Problem.Raw(reach), why)

  private def refusedWith(found: Either[Problems, AccessPolicy]): Vector[Problem] =
    found.left.toOption.fold(Vector.empty)(_.toVector)

  // ── the settings ───────────────────────────────────────────────────────────────────────────────

  test("the defaults: bound to 127.0.0.1 alone, loopback trusted as the owner, no other network accepted"):
    assertEquals(defaults.server.host.toVector.map(h => s"$h"), Vector("127.0.0.1"))
    val p = policy(defaults)
    assertEquals(p.trustedAs, AccessPolicy.Principal.Owner)
    assertEquals(p.proxy, AccessPolicy.Proxy.Undeclared)
    assertEquals(p.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("::1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(p.requirementOf(at("192.168.1.20")), None)
    assertEquals(p.requirementOf(at("100.101.102.103")), None)
    assert(AccessPolicy.forListener(defaults.auth, defaults.server).isRight)

  test("server.host is a list; an empty list is no setting of it"):
    val two = settings("""histmersion.server.host = ["127.0.0.1", "100.101.102.103"]""")
    assertEquals(two.server.host.toVector.map(h => s"$h"), Vector("127.0.0.1", "100.101.102.103"))
    assert(AppConfig.loadWithUserText(ConfigFactory.empty, "histmersion.server.host = []").isLeft)

  test("HISTMERSION_BIND lists the addresses comma-separated, its spaces and empty entries dropped"):
    val env = ConfigFactory.parseString("""HISTMERSION_BIND = " 127.0.0.1, ,100.101.102.103 " """)
    assertEquals(AppConfig.load(env).server.host.toVector.map(h => s"$h"), Vector("127.0.0.1", "100.101.102.103"))
    val one = ConfigFactory.parseString("""HISTMERSION_BIND = "0.0.0.0" """)
    assertEquals(AppConfig.load(one).server.host.toVector.map(h => s"$h"), Vector("0.0.0.0"))

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

  test("the refused-networks example of reference.conf, as written there, starts on 0.0.0.0 and :: with no key"):
    val reference = Source.fromResource("reference.conf").getLines().toList
    val example = reference
      .find(l => l.contains("refused-networks = [\"0.0.0.0/0\""))
      .getOrElse(fail("reference.conf has no refused-networks example"))
    val line = example.drop(example.indexOf("refused-networks")).trim
    val c = settings(s"""histmersion { server.host = ["0.0.0.0", "::"], auth { $line } }""")
    assert(AccessPolicy.forListener(c.auth, c.server).isRight)
    assertEquals(policy(c).requirementOf(at("192.168.1.20")), Some(AccessPolicy.Requirement.Refused))

  test("the profiles of the docs read and start"):
    val local = settings("")
    // loopback plus the tailnet address: reachable from the tailnet's devices and this machine, not from the LAN
    // (an address of a family is reached from that whole family, so the rest of IPv4 is named as refused)
    val tailscale = settings(
      """histmersion {
        |  server.host = ["127.0.0.1", "::1", "100.101.102.103"]
        |  auth {
        |    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10", "fd7a:115c:a1e0::/48"]
        |    refused-networks = ["0.0.0.0/0", "::/0"]
        |    hosts = ["mymac.tail1234.ts.net"]
        |  }
        |}""".stripMargin
    )
    // every interface, no key: the trusted networks in, every other network refused
    val wildcard = settings(
      """histmersion {
        |  server.host = ["0.0.0.0"]
        |  auth {
        |    trusted-networks = ["127.0.0.0/8", "100.64.0.0/10"]
        |    refused-networks = ["0.0.0.0/0"]
        |  }
        |}""".stripMargin
    )
    val institution = settings(
      """histmersion {
        |  server { host = ["0.0.0.0"], public-url = "https://histmersion.example.edu" }
        |  auth {
        |    api-key = "k3y"
        |    trusted-networks = []
        |    authenticated-networks = ["0.0.0.0/0", "::/0"]
        |    hosts = ["histmersion.example.edu"]
        |  }
        |}""".stripMargin
    )
    // a proxy on this machine in front of a loopback-only server that still trusts loopback for the owner's CLI
    val proxied = settings(
      """histmersion {
        |  server { host = ["127.0.0.1"], public-url = "https://histmersion.example.edu" }
        |  auth { api-key = "k3y", proxy = "local", hosts = ["histmersion.example.edu"] }
        |}""".stripMargin
    )
    List(local, tailscale, wildcard, institution, proxied).foreach(c =>
      assert(AccessPolicy.forListener(c.auth, c.server).isRight, s"${c.server.host} ${c.auth}")
    )
    val t = policy(tailscale)
    assertEquals(t.requirementOf(at("100.101.102.103")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(t.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(t.requirementOf(at("192.168.1.20")), Some(AccessPolicy.Requirement.Refused))
    List("MyMac.tail1234.ts.net:8000", "100.101.102.103:8000", "[::1]:8000")
      .foreach(h => assert(t.answersTo(AccessPolicy.HostName.ofHeader(h)), h))
    val w = policy(wildcard)
    assertEquals(w.requirementOf(at("100.101.102.103")), Some(AccessPolicy.Requirement.Trusted))
    assertEquals(w.requirementOf(at("192.168.1.20")), Some(AccessPolicy.Requirement.Refused))
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
    assertEquals(policy(proxied).proxy, AccessPolicy.Proxy.Local)

  test("every wrong setting is named, not just the first"):
    val wrong =
      auth().modify(_.trustedNetworks).setTo(List(AuthConfig.Network("127.0.0.0/8"), AuthConfig.Network("lan")))
    val all = wrong
      .modify(_.authenticatedNetworks)
      .setTo(List(AuthConfig.Network("10.0.0.0/33")))
      .modify(_.refusedNetworks)
      .setTo(List(AuthConfig.Network("everything")))
      .modify(_.trustedAs)
      .setTo(AuthConfig.TrustedAs("root"))
      .modify(_.proxy)
      .setTo(AuthConfig.Proxy("nginx"))
      .modify(_.origins)
      .setTo(List(AuthConfig.Origin("ftp://x")))
    def setting(key: String, fault: Problem.Config.Fault): Problem =
      Problem.Config.Setting(Problem.SettingKey(key), fault)
    assertEquals(
      AccessPolicy.from(all, defaults.server).left.map(_.toVector),
      Left(
        Vector(
          setting("auth.trusted-networks", Problem.Config.Fault.Malformed(Problem.Raw("lan"))),
          setting("auth.authenticated-networks", Problem.Config.Fault.Malformed(Problem.Raw("10.0.0.0/33"))),
          setting("auth.refused-networks", Problem.Config.Fault.Malformed(Problem.Raw("everything"))),
          setting("auth.origins", Problem.Config.Fault.Malformed(Problem.Raw("ftp://x"))),
          setting("auth.trusted-as", Problem.Config.Fault.Unknown(Problem.Raw("root"))),
          setting("auth.proxy", Problem.Config.Fault.Unknown(Problem.Raw("nginx")))
        )
      )
    )

  test("one network named in two lists contradicts itself, whichever two"):
    def conflict(later: String, earlier: String): Problem =
      Problem.Config.Setting(
        Problem.SettingKey(later),
        Problem.Config.Fault.Conflicts(Problem.SettingKey(earlier))
      )
    def clashes(a: AuthConfig): Vector[Problem] = refusedWith(AccessPolicy.from(a, defaults.server))
    assertEquals(
      clashes(auth(authenticated = List("127.0.0.0/8"))),
      Vector(conflict("auth.authenticated-networks", "auth.trusted-networks"))
    )
    // the same network however it is written: host bits cleared
    assertEquals(
      clashes(auth(refused = List("127.1.2.3/8"))),
      Vector(conflict("auth.refused-networks", "auth.trusted-networks"))
    )
    assertEquals(
      clashes(auth(authenticated = List("10.0.0.0/8"), refused = List("10.0.0.0/8"))),
      Vector(conflict("auth.refused-networks", "auth.authenticated-networks"))
    )
    // a bare address is a network of one: ::1 is ::1/128
    assertEquals(
      refusedWith(AccessPolicy.forListener(auth(refused = List("::1")), bound("127.0.0.1"))),
      Vector(conflict("auth.refused-networks", "auth.trusted-networks"))
    )
    // nested networks are no contradiction: the longest prefix decides
    assert(AccessPolicy.from(auth(refused = List("0.0.0.0/0", "::/0")), defaults.server).isRight)

  test("the names a request may give: loopback's, every bind's, the public URL's and the listed ones"):
    val p = policy(
      settings(
        """histmersion {
          |  server.host = ["192.168.1.5", "100.101.102.103", "0.0.0.0"]
          |  auth.hosts = ["Archive.Example.org."]
          |}""".stripMargin
      )
    )
    List(
      "localhost:8000",
      "127.0.0.1",
      "[::1]:8000",
      "192.168.1.5:8000",
      "100.101.102.103",
      "archive.example.org",
      "LOCALHOST"
    ).foreach(h => assert(p.answersTo(AccessPolicy.HostName.ofHeader(h)), h))
    // a wildcard bind adds no name
    List("rebound.example", "127.0.0.2", "localhost.evil.example", "[::2]", "0.0.0.0")
      .foreach(h => assert(!p.answersTo(AccessPolicy.HostName.ofHeader(h)), h))

  // ── the start-up rule ──────────────────────────────────────────────────────────────────────────

  test("bound to loopback, the default networks start, with a key or without"):
    assert(listener(auth(), "127.0.0.1").isRight)
    assert(listener(auth(key = "k"), "127.0.0.1").isRight)
    assert(listener(auth(), "::1").isRight)
    assert(listener(auth(), "localhost").isRight)
    assert(listener(auth(), "127.0.0.1", "::1", "localhost").isRight)

  test("bound beyond loopback with an empty key and only loopback trusted: refused, naming the settings"):
    val refused = listener(auth(), "0.0.0.0")
    assertEquals(refused, Left(exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted)))
    val said = refused.left.toOption.get.sentences.mkString
    List(
      "server.host",
      "auth.trusted-networks",
      "auth.authenticated-networks",
      "auth.refused-networks",
      "HISTMERSION_BIND"
    ).foreach(k => assert(said.contains(k), said))

  test("an authenticated network with no key leaves it open: refused until the key is set, or the network refused"):
    assertEquals(
      listener(auth(authenticated = List("0.0.0.0/0")), "0.0.0.0"),
      Left(exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.NoKey))
    )
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "0.0.0.0").isRight)
    assert(listener(auth(authenticated = List("10.0.0.0/8"), refused = List("0.0.0.0/0")), "0.0.0.0").isLeft)

  test("a network trusted as a whole needs no key; part of the reach left unlisted is still refused"):
    assert(listener(auth(trusted = List("0.0.0.0/0")), "0.0.0.0").isRight)
    assertEquals(
      listener(auth(trusted = List("0.0.0.0/1"), authenticated = Nil), "0.0.0.0"),
      Left(exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted))
    )
    assert(
      listener(auth(key = "k", trusted = List("0.0.0.0/1"), authenticated = List("128.0.0.0/1")), "0.0.0.0").isRight
    )

  test("refused networks account for a reach: 0.0.0.0 with no key starts when every other network is refused"):
    assert(
      listener(auth(trusted = List("127.0.0.0/8", "100.64.0.0/10"), refused = List("0.0.0.0/0")), "0.0.0.0").isRight
    )
    // half the reach refused, half unlisted: still open
    assertEquals(
      listener(auth(refused = List("128.0.0.0/1")), "0.0.0.0"),
      Left(exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted))
    )
    // the wildcard of both families needs both refused
    assertEquals(
      listener(auth(refused = List("0.0.0.0/0")), "::"),
      Left(exposed("::", "::/0", Problem.Config.Exposure.Unlisted))
    )
    assert(listener(auth(refused = List("0.0.0.0/0", "::/0")), "::").isRight)

  test("an address is reached from its whole family; the dual-stack wildcard and a name from both"):
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "192.168.1.5").isRight)
    assertEquals(
      listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "::"),
      Left(exposed("::", "::/0", Problem.Config.Exposure.Unlisted))
    )
    assertEquals(
      listener(auth(key = "k", authenticated = List("0.0.0.0/0")), "myhost.example"),
      Left(exposed("myhost.example", "::/0", Problem.Config.Exposure.Unlisted))
    )
    assert(listener(auth(key = "k", authenticated = List("0.0.0.0/0", "::/0")), "::").isRight)
    // ::1 alone is reached from IPv6 loopback only
    assertEquals(
      listener(auth(trusted = List("127.0.0.0/8")), "::1"),
      Left(exposed("::1", "::1/128", Problem.Config.Exposure.Unlisted))
    )

  test("several bind addresses: each is checked on its own, and every open one is named"):
    // loopback and a tailnet address: the tailnet address is reached from all of IPv4
    assertEquals(
      listener(auth(trusted = List("127.0.0.0/8", "::1/128", "100.64.0.0/10")), "127.0.0.1", "100.101.102.103"),
      Left(exposed("100.101.102.103", "0.0.0.0/0", Problem.Config.Exposure.Unlisted))
    )
    assertEquals(
      refusedWith(listener(auth(trusted = List("127.0.0.0/8")), "127.0.0.1", "::1", "192.168.1.5")),
      Vector(
        exposed("::1", "::1/128", Problem.Config.Exposure.Unlisted),
        exposed("192.168.1.5", "0.0.0.0/0", Problem.Config.Exposure.Unlisted)
      )
    )

  /** Exhaustive over three zones of IPv4 — loopback, the tailnet, the rest — each of the four kinds (trusted,
    * authenticated, refused, unlisted), the key set or not, and every non-empty set of three bind addresses (loopback,
    * the tailnet address, the wildcard), against the rule written out: an address is open when some zone it is reached
    * from is unlisted, or authenticated with no key. Loopback reaches loopback only, the other two all of IPv4; an
    * unlisted loopback or tailnet zone takes the kind of the rest (`0.0.0.0/0` holds it: the longest prefix decides).
    */
  test("the multi-bind start-up rule, exhaustively: zones x kinds x key x binds"):
    enum Kind derives CanEqual:
      case Trusted, Authenticated, Refused, Unlisted
    val zones = List("127.0.0.0/8", "100.64.0.0/10", "0.0.0.0/0")
    val addresses = List("127.0.0.1", "100.101.102.103", "0.0.0.0")
    val reached = Map("127.0.0.1" -> List(0), "100.101.102.103" -> List(0, 1, 2), "0.0.0.0" -> List(0, 1, 2))
    val bindSets = (1 to addresses.size).flatMap(addresses.combinations).toList
    var cases = 0
    for
      kinds <- for a <- Kind.values.toList; b <- Kind.values.toList; c <- Kind.values.toList yield List(a, b, c)
      key <- List(true, false)
      bs <- bindSets
    do
      def listed(k: Kind): List[String] = zones.zip(kinds).collect { case (z, kind) if kind == k => z }
      val a = auth(
        key = if key then "k" else "",
        trusted = listed(Kind.Trusted),
        authenticated = listed(Kind.Authenticated),
        refused = listed(Kind.Refused)
      )
      def effective(zone: Int): Kind = if kinds(zone) == Kind.Unlisted then kinds(2) else kinds(zone)
      def open(zone: Int): Boolean =
        effective(zone) == Kind.Unlisted || (effective(zone) == Kind.Authenticated && !key)
      val wantOpen = bs.filter(b => reached(b).exists(open))
      val got = listener(a, bs.head, bs.tail*)
      val gotOpen = refusedWith(got).collect { case Problem.Config.Exposed(b, _, _) => s"$b" }
      assertEquals(gotOpen, wantOpen.toVector, s"$kinds key=$key binds=$bs")
      assertEquals(got.isRight, wantOpen.isEmpty, s"$kinds key=$key binds=$bs")
      cases += 1
    assertEquals(cases, 64 * 2 * 7)

  // ── the proxy rule ─────────────────────────────────────────────────────────────────────────────

  private def public(url: String): ServerConfig =
    bound("127.0.0.1").modify(_.publicUrl).setTo(ServerConfig.PublicUrl(url))

  test("an https public URL with loopback trusted and no proxy declared: refused, naming the settings"):
    val refused = AccessPolicy.forListener(auth(), public("https://histmersion.example.edu"))
    assertEquals(refused, Left(Problem.Config.ProxyUndeclared(Problem.Raw("https://histmersion.example.edu"))))
    val said = refused.left.toOption.get.sentences.mkString
    List("server.public-url", "auth.proxy", "auth.trusted-networks", "auth.api-key")
      .foreach(k => assert(said.contains(k), said))

  test("the proxy rule: https x loopback trusted x proxy declared, only all three refuse"):
    for
      url <- List("https://h.example", "http://h.example", "HTTPS://H.example:8443/x", "http://localhost:8000")
      trusted <- List(List("127.0.0.0/8", "::1/128"), List("::1/128"), List("127.0.0.1/32"), Nil)
      proxy <- List("none", "local")
    do
      val https = url.toLowerCase.startsWith("https")
      val want = https && trusted.nonEmpty && proxy == "none"
      // everything else authenticated with the key, so the bind's reach is accounted for whatever is trusted
      val authenticated = List("0.0.0.0/0", "::/0")
      val got =
        AccessPolicy.forListener(
          auth(key = "k", trusted = trusted, authenticated = authenticated, proxy = proxy),
          public(url)
        )
      assertEquals(got.isLeft, want, s"$url $trusted $proxy $got")
      if want then assertEquals(got, Left(Problem.Config.ProxyUndeclared(Problem.Raw(url))))

  test("a declared proxy changes no network's requirement; the start-up problems are all reported together"):
    val p = policy(settings("""histmersion.auth.proxy = "local""""))
    assertEquals(p.requirementOf(at("127.0.0.1")), Some(AccessPolicy.Requirement.Trusted))
    val both =
      AccessPolicy.forListener(auth(), bound("0.0.0.0").modify(_.publicUrl).setTo(ServerConfig.PublicUrl("https://x")))
    assertEquals(
      refusedWith(both),
      Vector(
        exposed("0.0.0.0", "0.0.0.0/0", Problem.Config.Exposure.Unlisted),
        Problem.Config.ProxyUndeclared(Problem.Raw("https://x"))
      )
    )

  test("the server refuses to start before it opens anything"):
    val open = defaults.modify(_.auth).setTo(auth())
    val thrown = intercept[IllegalStateException] {
      supervised(Composition.server(open, ServerConfig.Host("0.0.0.0"), ServerConfig.Port(0)))
    }
    assert(
      thrown.getMessage.contains("server.host lists '0.0.0.0', which can be reached from 0.0.0.0/0"),
      thrown.getMessage
    )
    val two = intercept[IllegalStateException] {
      supervised(Composition.listener(open, binds("127.0.0.1", "100.101.102.103"), ServerConfig.Port(0)))
    }
    assert(two.getMessage.contains("server.host lists '100.101.102.103'"), two.getMessage)
