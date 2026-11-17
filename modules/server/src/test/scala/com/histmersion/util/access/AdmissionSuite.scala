package com.histmersion
package util.access

import java.net.InetAddress

import hearth.kindlings.optics.syntax.*
import org.ekrich.config.ConfigFactory

import com.histmersion.config.{AppConfig, AuthConfig}
import com.histmersion.util.problem.Problem

/** How the access gate decides, as a pure function of the peer, the port, the method, the path, the `Host`, `Origin`
  * and `Referer` headers, whether a proxy forwarded it, what credential it presented, and the policy — tried over every
  * combination that matters, so no peer address has to be faked.
  */
class AdmissionSuite extends munit.FunSuite:
  import Admission.*

  private val defaults = AppConfig.load(ConfigFactory.empty)
  private val agent = AgentPaths(Path("/mcp/agent"), Path("/api/agents/quota/claude"))

  /** Loopback trusted, the tailnet trusted, the LAN authenticated with a key, `10.0.0.0/8` refused (and a guest range
    * inside the LAN refused too), everything else unlisted; no proxy declared.
    */
  private val policy: AccessPolicy = policyWith("none")

  /** [[policy]] with a proxy on this machine declared. */
  private val proxied: AccessPolicy = policyWith("local")

  private def policyWith(proxy: String): AccessPolicy =
    val auth = defaults.auth
      .modify(_.proxy)
      .setTo(AuthConfig.Proxy(proxy))
      .modify(_.apiKey)
      .setTo(AuthConfig.ApiKey("k3y"))
      .modify(_.trustedNetworks)
      .setTo(List("127.0.0.0/8", "::1/128", "100.64.0.0/10").map(AuthConfig.Network(_)))
      .modify(_.authenticatedNetworks)
      .setTo(List("192.168.0.0/16").map(AuthConfig.Network(_)))
      .modify(_.refusedNetworks)
      .setTo(List("10.0.0.0/8", "192.168.50.0/24").map(AuthConfig.Network(_)))
      .modify(_.hosts)
      .setTo(List(AuthConfig.HostName("mymac.tail1234.ts.net")))
      .modify(_.origins)
      .setTo(List(AuthConfig.Origin("https://proxy.example")))
    AccessPolicy.from(auth, defaults.server).fold(e => fail(s"$e"), identity)

  private val loopback = InetAddress.getByName("127.0.0.1")
  private val tailnet = InetAddress.getByName("100.101.102.103")
  private val lan = InetAddress.getByName("192.168.1.20")
  private val refusedPeer = InetAddress.getByName("10.1.2.3")
  private val guest = InetAddress.getByName("192.168.50.7")
  private val stranger = InetAddress.getByName("203.0.113.9")

  private def request(
      peer: InetAddress = loopback,
      method: Method = Method.Read,
      path: String = "/api/backups/x",
      host: Option[String] = Some("localhost:8000"),
      origin: Option[String] = None,
      referer: Option[String] = None,
      forwarded: Boolean = false,
      presented: Presented = Presented.Absent
  ): Request =
    Request(
      peer,
      AccessPolicy.Port(8000),
      method,
      Path(path),
      host.map(HeaderText(_)),
      origin.map(HeaderText(_)),
      referer.map(HeaderText(_)),
      Forwarded.of(forwarded),
      presented
    )

  private def decided(r: Request, p: AccessPolicy = policy): Either[Problem, Admission] = decide(r, p, agent)

  private val unauthenticated = Left(Problem.Refused.Unauthenticated(Problem.Refused.Credential.ApiKey))
  private val crossSite = Left(Problem.Refused.Policy(Problem.Refused.Rule.CrossSite))
  private val undeclaredProxy = Left(Problem.Refused.Policy(Problem.Refused.Rule.UndeclaredProxy))

  // ── Host ───────────────────────────────────────────────────────────────────────────────────────

  test("a foreign Host is misdirected (421), whoever sends it and whatever it carries"):
    for
      peer <- List(loopback, tailnet, lan)
      presented <- Presented.values.toList
      method <- Method.values.toList
    do
      assertEquals(
        decided(request(peer = peer, method = method, host = Some("rebound.example:8000"), presented = presented)),
        Left(Problem.Refused.Misdirected(Problem.Raw("rebound.example")))
      )
    assertEquals(decided(request(host = None)), Left(Problem.Refused.Misdirected(Problem.Raw(""))))

  test("the names the server answers to pass the Host check"):
    List("localhost:8000", "127.0.0.1:8000", "[::1]:8000", "mymac.tail1234.ts.net:8000", "LOCALHOST")
      .foreach(h => assertEquals(decided(request(host = Some(h))), Right(Trusted), h))

  // ── Origin ─────────────────────────────────────────────────────────────────────────────────────

  test("a change sent by another site's page is refused (403), even from a trusted network with the key"):
    for
      peer <- List(loopback, tailnet, lan)
      presented <- List(Presented.Absent, Presented.ApiKey)
      (origin, referer) <- List(
        (Some("https://evil.example"), None),
        (None, Some("https://evil.example/page")),
        (Some("null"), None),
        (Some("http://localhost:3000"), None), // another program on this machine is another site
        (Some("https://evil.example"), Some("http://localhost:8000/"))
      )
    do
      assertEquals(
        decided(request(peer, Method.Change, origin = origin, referer = referer, presented = presented)),
        crossSite
      )

  test("a change from this server's own page, a listed origin, or no page at all passes the Origin check"):
    List(
      (Some("http://localhost:8000"), None),
      (Some("http://127.0.0.1:8000"), None),
      (Some("http://[::1]:8000"), None),
      (Some("https://localhost:8000"), None),
      (Some("http://mymac.tail1234.ts.net:8000"), None),
      (Some("https://proxy.example"), None),
      (None, Some("http://localhost:8000/explore#x")),
      (None, None)
    ).foreach((o, r) => assertEquals(decided(request(method = Method.Change, origin = o, referer = r)), Right(Trusted)))

  test("a read is not held to the Origin check: another site can send it but cannot read the answer"):
    assertEquals(decided(request(origin = Some("https://evil.example"))), Right(Trusted))

  // ── networks and credentials ───────────────────────────────────────────────────────────────────

  test("a trusted network's request acts as the owner, reads and changes alike, with or without a key"):
    for
      peer <- List(loopback, tailnet, InetAddress.getByName("::1"))
      method <- List(Method.Read, Method.Change, Method.Other)
      presented <- Presented.values.toList
    do assertEquals(decided(request(peer, method, presented = presented)), Right(Trusted))

  test("an authenticated network's request needs the key — GET included — unless it is a public read"):
    for method <- List(Method.Read, Method.Change, Method.Other) do
      assertEquals(decided(request(lan, method)), unauthenticated, method)
      assertEquals(decided(request(lan, method, presented = Presented.Wrong)), unauthenticated, method)
      assertEquals(decided(request(lan, method, presented = Presented.ApiKey)), Right(KeyHolder), method)
    List("/api/backups/x", "/api/settings", "/api/agent-state", "/api/runs/1/transcript", "/api/config", "/mcp")
      .foreach(p => assertEquals(decided(request(lan, path = p)), unauthenticated, p))

  test("a run token is the key on the quota path only"):
    assertEquals(
      decided(request(lan, Method.Change, path = "/api/agents/quota/claude", presented = Presented.RunToken)),
      Right(RunToken)
    )
    assertEquals(
      decided(request(lan, Method.Change, path = "/api/agents/quota/claude-plan", presented = Presented.RunToken)),
      unauthenticated
    )

  test("the agent endpoint is left to its run-token gate, from any accepted network"):
    List("/mcp/agent", "/mcp/agent/skeptic").foreach { p =>
      assertEquals(decided(request(lan, Method.Change, path = p)), Right(Agent), p)
      assertEquals(decided(request(loopback, Method.Change, path = p)), Right(Agent), p)
    }
    assertEquals(decided(request(lan, Method.Change, path = "/mcp/agentx")), unauthenticated)

  test("a network no setting lists is refused (403), the public probes and the agent endpoint included"):
    for path <- List("/api/health", "/api/backups/x", "/", "/mcp/agent") do
      assertEquals(
        decided(request(stranger, path = path, presented = Presented.ApiKey)),
        Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
      )

  test("a refused network is refused (403) with its own rule, whatever it sends; inside an authenticated one too"):
    for
      peer <- List(refusedPeer, guest)
      path <- List("/api/health", "/api/backups/x", "/", "/mcp/agent")
      presented <- Presented.values.toList
      host <- List(Some("localhost:8000"), Some("rebound.example"), None)
    do
      assertEquals(
        decided(request(peer, path = path, presented = presented, host = host)),
        Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused)),
        s"$peer $path ${presented.productPrefix} $host"
      )
    assertEquals(decided(request(lan, presented = Presented.ApiKey)), Right(KeyHolder), "the LAN around the guests")

  /** Every combination of the network kind (trusted loopback and tailnet, authenticated, refused, unlisted), the
    * method, the credential, a proxy's mark, the proxy setting (none, local), the `Host` (answered, foreign, missing),
    * the page a change comes from (none, the server's own, a listed one, another site's by `Origin` or by `Referer`,
    * `null`) and the path (gated, public, the agent endpoint, the quota path), against the decision written out as a
    * table of rules in order.
    */
  test("the decision, exhaustively: network kind x method x credential x mark x proxy x Host x origin x path"):
    enum Net derives CanEqual:
      case Trusted, Authenticated, Refused, Unlisted
    val peers = List(loopback -> Net.Trusted, tailnet -> Net.Trusted, lan -> Net.Authenticated) ++
      List(refusedPeer -> Net.Refused, guest -> Net.Refused, stranger -> Net.Unlisted)
    val hosts = List(Some("localhost:8000") -> true, Some("rebound.example:8000") -> false, None -> false)
    // (Origin, Referer, whether a change from it is the server's own)
    val pages = List(
      (None, None, true),
      (Some("http://localhost:8000"), None, true),
      (Some("https://proxy.example"), None, true),
      (None, Some("http://localhost:8000/explore"), true),
      (Some("https://evil.example"), None, false),
      (None, Some("https://evil.example/x"), false),
      (Some("null"), None, false)
    )
    val paths = List("/api/backups/x", "/api/health", "/mcp/agent/skeptic", "/api/agents/quota/claude")
    def expected(
        net: Net,
        method: Method,
        presented: Presented,
        forwarded: Boolean,
        proxyDeclared: Boolean,
        host: Option[String],
        hostOk: Boolean,
        ownPage: Boolean,
        path: String
    ): Either[Problem, Admission] =
      if net == Net.Unlisted then Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
      else if net == Net.Refused then Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
      else if forwarded && !proxyDeclared then undeclaredProxy
      else if !hostOk then Left(Problem.Refused.Misdirected(Problem.Raw(host.fold("")(_.takeWhile(_ != ':')))))
      else if method == Method.Change && !ownPage then crossSite
      else if path.startsWith("/mcp/agent") then Right(Agent)
      else if net == Net.Trusted && !forwarded then Right(Trusted)
      else if presented == Presented.ApiKey then Right(KeyHolder)
      else if presented == Presented.RunToken && path == "/api/agents/quota/claude" then Right(RunToken)
      else if method == Method.Read && path == "/api/health" then Right(Public)
      else unauthenticated
    var cases = 0
    for
      (peer, net) <- peers
      method <- Method.values.toList
      presented <- Presented.values.toList
      forwarded <- List(false, true)
      (p, proxyDeclared) <- List(policy -> false, proxied -> true)
      (host, hostOk) <- hosts
      (origin, referer, ownPage) <- pages
      path <- paths
    do
      val r = request(peer, method, path, host, origin, referer, forwarded, presented)
      assertEquals(
        decided(r, p),
        expected(net, method, presented, forwarded, proxyDeclared, host, hostOk, ownPage, path),
        s"${net.productPrefix} $peer ${method.productPrefix} ${presented.productPrefix} forwarded=$forwarded " +
          s"proxy=$proxyDeclared $host $origin $referer $path"
      )
      cases += 1
    assertEquals(cases, 6 * 3 * 4 * 2 * 2 * 3 * 7 * 4)

  test("with a proxy declared, a proxied request earns no trust from its peer address: it needs the key"):
    assertEquals(decided(request(loopback, forwarded = true), proxied), unauthenticated)
    assertEquals(decided(request(loopback, forwarded = true, presented = Presented.ApiKey), proxied), Right(KeyHolder))
    assertEquals(decided(request(loopback, forwarded = true, path = "/api/health"), proxied), Right(Public))
    assertEquals(decided(request(loopback), proxied), Right(Trusted), "the owner's own unmarked request")

  test("with no proxy declared, a proxied request is refused (403) from any accepted network, whatever it carries"):
    for
      peer <- List(loopback, tailnet, lan)
      presented <- Presented.values.toList
      path <- List("/api/health", "/", "/api/backups/x", "/mcp/agent")
      host <- List(Some("localhost:8000"), Some("rebound.example"), None)
    do
      assertEquals(
        decided(request(peer, path = path, host = host, presented = presented, forwarded = true)),
        undeclaredProxy
      )
    // a refused or unlisted network keeps its own rule
    assertEquals(
      decided(request(refusedPeer, forwarded = true)),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
    )
    assertEquals(
      decided(request(stranger, forwarded = true)),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
    )

  test("the headers that mark a request as forwarded: the standard one and the four de-facto ones"):
    assertEquals(
      Forwarded.Headers,
      List("Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP")
    )

  // ── the public allow-list, pinned ──────────────────────────────────────────────────────────────

  test("the public allow-list: the probes and the web UI's own page and assets, read only"):
    val public = List(
      "/api/health",
      "/api/version",
      "/",
      "/explore",
      "/library",
      "/research",
      "/settings",
      "/setup",
      "/place/x",
      "/acquire",
      "/web/app.bundle.js",
      "/web/style.css",
      "/web/vendor/maplibre-gl/maplibre-gl.css"
    )
    val gated = List(
      "/api/config",
      "/api/backups",
      "/api/backups/x",
      "/api/settings",
      "/api/agent-state",
      "/api/sources",
      "/api/ui/counters",
      "/api/tiles/x/1/2/3",
      "/source/x",
      "/article/x",
      "/mcp",
      "/api",
      "/api/healthz",
      "/webx",
      "/place/x/y"
    )
    public.foreach(p => assert(isPublic(Method.Read, Path(p)), p))
    gated.foreach(p => assert(!isPublic(Method.Read, Path(p)), p))
    public.foreach(p => assert(!isPublic(Method.Change, Path(p)) && !isPublic(Method.Other, Path(p)), p))
    public.foreach(p => assertEquals(decided(request(lan, path = p)), Right(Public), p))

  test("a request target's path drops its query and fragment"):
    assertEquals(Path.ofTarget("/api/health?x=1"), Path("/api/health"))
    assertEquals(Path.ofTarget("/explore#a"), Path("/explore"))

  test("methods: GET and HEAD read; POST, PUT, PATCH and DELETE change; the rest are neither"):
    assertEquals(
      List("GET", "head", "POST", "PUT", "patch", "DELETE", "OPTIONS", "TRACE").map(Method.of),
      List(
        Method.Read,
        Method.Read,
        Method.Change,
        Method.Change,
        Method.Change,
        Method.Change,
        Method.Other,
        Method.Other
      )
    )

  // ── the key comparison ─────────────────────────────────────────────────────────────────────────

  test("the bearer value of an Authorization header"):
    assertEquals(Bearer.of("Bearer  k3y "), Some(Bearer("k3y")))
    assertEquals(Bearer.of("Basic a2V5"), None)
    assertEquals(Bearer.of(null), None)

  test("the key comparison: equal keys only, an empty key matches nothing"):
    val key = AuthConfig.ApiKey("k3y-0123456789")
    assert(Bearer("k3y-0123456789").matches(key))
    List("k3y-012345678", "k3y-01234567890", "K3Y-0123456789", "", "k3y-0123456788")
      .foreach(b => assert(!Bearer(b).matches(key), b))
    assert(!Bearer("").matches(AuthConfig.ApiKey("")))
    assert(!Bearer("anything").matches(AuthConfig.ApiKey("")))

  test("the key comparison is MessageDigest.isEqual over SHA-256 digests: its time does not depend on the content"):
    // the comparator's own building blocks, checked as the JDK documents them: isEqual reads every byte of two
    // digests of one fixed length, so neither where two keys first differ nor how long they are shows in its time
    val sha = java.security.MessageDigest.getInstance("SHA-256")
    val a = sha.digest("k3y".getBytes("UTF-8"))
    val b = sha.digest("a much longer key that differs at the first byte".getBytes("UTF-8"))
    assertEquals(a.length, b.length)
    assert(!java.security.MessageDigest.isEqual(a, b))
    val source = scala.io.Source.fromFile("modules/server/src/main/scala/com/histmersion/util/access/Admission.scala")
    try
      val text = source.mkString
      assert(text.contains("MessageDigest.isEqual(sha256("), "the comparator compares digests with isEqual")
      assert(!text.contains("== expected") && !text.contains("!= expected"), "no early-exit string comparison")
    finally source.close()
