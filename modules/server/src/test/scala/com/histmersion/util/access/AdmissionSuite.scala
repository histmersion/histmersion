package com.histmersion
package util.access

import java.net.InetAddress

import scala.jdk.CollectionConverters.*

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
    * inside the LAN refused too), everything else unlisted; no proxy network.
    */
  private val policy: AccessPolicy = policyWith(Nil)

  /** [[policy]] with a proxy on this machine declared: loopback is a proxy network. */
  private val proxied: AccessPolicy = policyWith(List("127.0.0.0/8", "::1/128"))

  /** [[policy]] with a proxy network that holds none of this suite's peers (a documentation range). */
  private val proxiedElsewhere: AccessPolicy = policyWith(List("198.51.100.0/24"))

  private def policyWith(proxyNetworks: List[String]): AccessPolicy =
    val auth = defaults.auth
      .modify(_.proxyNetworks)
      .setTo(proxyNetworks.map(AuthConfig.Network(_)))
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
      forwarding: Forwarding = Forwarding.Absent,
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
      forwarding,
      presented
    )

  private def headers(named: (String, String)*): Forwarding =
    Forwarding.of(name => HeaderText.all(named.collect { case (n, v) if n.equalsIgnoreCase(name) => v }.asJava))

  private val viaOnly: Forwarding = headers("Via" -> "1.1 proxy.example")
  private def forwardedFor(chain: String): Forwarding = headers("X-Forwarded-For" -> chain)

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
    * method, the credential, a proxy's mark, the proxy networks (empty, one that covers the peer, one that does not),
    * the `Host` (answered, foreign, missing), the page a change comes from (none, the server's own, a listed one,
    * another site's by `Origin` or by `Referer`, `null`) and the path (gated, public, the agent endpoint, the quota
    * path), against the decision written out as a table of rules in order.
    */
  test("the decision, exhaustively: network kind x method x credential x mark x proxy x Host x origin x path"):
    enum Net derives CanEqual:
      case Trusted, Authenticated, Refused, Unlisted
    enum Proxies derives CanEqual:
      case Empty, CoverPeer, Elsewhere
    val peers = List(loopback -> Net.Trusted, tailnet -> Net.Trusted, lan -> Net.Authenticated) ++
      List(refusedPeer -> Net.Refused, guest -> Net.Refused, stranger -> Net.Unlisted)
    // the peer itself as the proxy network (a network of one), for each peer
    val covering = peers.map((peer, _) => peer -> policyWith(List(peer.getHostAddress))).toMap
    def policyOf(peer: InetAddress, proxies: Proxies): AccessPolicy = proxies match
      case Proxies.Empty     => policy
      case Proxies.CoverPeer => covering(peer)
      case Proxies.Elsewhere => proxiedElsewhere
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
        proxies: Proxies,
        host: Option[String],
        hostOk: Boolean,
        ownPage: Boolean,
        path: String
    ): Either[Problem, Admission] =
      if net == Net.Unlisted then Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
      else if net == Net.Refused then Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
      else if forwarded && proxies != Proxies.CoverPeer then undeclaredProxy
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
      (forwarding, forwarded) <- List(Forwarding.Absent -> false, viaOnly -> true)
      proxies <- Proxies.values.toList
      (host, hostOk) <- hosts
      (origin, referer, ownPage) <- pages
      path <- paths
    do
      val r = request(peer, method, path, host, origin, referer, forwarding, presented)
      assertEquals(
        decided(r, policyOf(peer, proxies)),
        expected(net, method, presented, forwarded, proxies, host, hostOk, ownPage, path),
        s"${net.productPrefix} $peer ${method.productPrefix} ${presented.productPrefix} forwarded=$forwarded " +
          s"proxies=${proxies.productPrefix} $host $origin $referer $path"
      )
      cases += 1
    assertEquals(cases, 6 * 3 * 4 * 2 * 3 * 3 * 7 * 4)

  test("from a proxy network, a request that reports no usable client address earns no trust: it needs the key"):
    for (label, forwarding) <- List(
        "Via only" -> viaOnly,
        "garbage" -> forwardedFor("garbage"),
        "empty" -> forwardedFor(""),
        "unknown" -> headers("Forwarded" -> "for=unknown")
      )
    do
      assertEquals(decided(request(loopback, forwarding = forwarding), proxied), unauthenticated, label)
      assertEquals(
        decided(request(loopback, forwarding = forwarding, presented = Presented.ApiKey), proxied),
        Right(KeyHolder),
        label
      )
      assertEquals(decided(request(loopback, forwarding = forwarding, path = "/api/health"), proxied), Right(Public))
    assertEquals(decided(request(loopback), proxied), Right(Trusted), "the owner's own unmarked request")
    // a load balancer elsewhere: its network authenticated (or trusted) and named as a proxy network
    val balancer = policyWith(List("192.168.1.20/32"))
    assertEquals(decided(request(lan, forwarding = viaOnly), balancer), unauthenticated)
    assertEquals(decided(request(lan, forwarding = viaOnly, presented = Presented.ApiKey), balancer), Right(KeyHolder))
    assertEquals(
      decided(
        request(InetAddress.getByName("192.168.1.21"), forwarding = viaOnly, presented = Presented.ApiKey),
        balancer
      ),
      undeclaredProxy,
      "its neighbour on the LAN is no proxy"
    )

  test("a declared proxy's reported client is judged as a direct peer: trusted with no key"):
    for
      (label, forwarding) <- List(
        "X-Forwarded-For" -> forwardedFor("100.101.102.103"),
        "Forwarded" -> headers("Forwarded" -> "for=100.101.102.103;proto=https"),
        "X-Real-IP" -> headers("X-Real-IP" -> "100.101.102.103"),
        "tailscale serve" -> headers(
          "X-Forwarded-For" -> "100.101.102.103",
          "X-Forwarded-Proto" -> "https",
          "X-Forwarded-Host" -> "mymac.tail1234.ts.net"
        )
      )
      method <- Method.values.toList
      presented <- Presented.values.toList
    do
      assertEquals(
        decided(request(loopback, method, forwarding = forwarding, presented = presented), proxied),
        Right(Trusted),
        s"$label ${method.productPrefix} ${presented.productPrefix}"
      )

  test("a declared proxy's reported client in an authenticated network needs the key"):
    val lanClient = forwardedFor("192.168.1.20")
    assertEquals(decided(request(loopback, forwarding = lanClient), proxied), unauthenticated)
    assertEquals(
      decided(request(loopback, forwarding = lanClient, presented = Presented.Wrong), proxied),
      unauthenticated
    )
    assertEquals(
      decided(request(loopback, forwarding = lanClient, presented = Presented.ApiKey), proxied),
      Right(KeyHolder)
    )
    assertEquals(decided(request(loopback, forwarding = lanClient, path = "/api/health"), proxied), Right(Public))

  test("a declared proxy's reported client that is refused or unlisted gets what a direct peer from it gets"):
    for
      (client, rule) <- List(
        "10.1.2.3" -> Problem.Refused.Rule.NetworkRefused,
        "192.168.50.7" -> Problem.Refused.Rule.NetworkRefused,
        "203.0.113.9" -> Problem.Refused.Rule.NetworkNotAccepted
      )
      presented <- Presented.values.toList
      path <- List("/api/health", "/", "/api/backups/x", "/mcp/agent")
    do
      val direct = decided(request(InetAddress.getByName(client), path = path, presented = presented))
      val forwarded =
        decided(request(loopback, path = path, forwarding = forwardedFor(client), presented = presented), proxied)
      assertEquals(forwarded, Left(Problem.Refused.Policy(rule)), s"$client $path")
      assertEquals(forwarded, direct, s"$client $path")

  test("for every peer kind, a declared proxy's report decides as a direct connection from that address would"):
    val peers = List(loopback, tailnet, lan, refusedPeer, guest, stranger, InetAddress.getByName("::1"))
    for
      client <- peers
      method <- Method.values.toList
      presented <- Presented.values.toList
      path <- List("/api/health", "/api/backups/x", "/mcp/agent", "/api/agents/quota/claude")
    do
      assertEquals(
        decided(
          request(loopback, method, path, forwarding = forwardedFor(client.getHostAddress), presented = presented),
          proxied
        ),
        decided(request(client, method, path, presented = presented), proxied),
        s"$client ${method.productPrefix} ${presented.productPrefix} $path"
      )

  test("chains: the rightmost address that is no declared proxy is the client"):
    val front = InetAddress.getByName("192.168.1.2")
    val twoProxies = policyWith(List("127.0.0.0/8", "::1/128", "192.168.1.2/32"))
    val chain = forwardedFor("203.0.113.9, 100.101.102.103")
    assertEquals(
      decided(request(loopback, forwarding = chain), proxied),
      Right(Trusted),
      "client 203.0.113.9, proxy1 100.101.102.103, proxy2 the loopback peer, only proxy2 declared: proxy1 is the client"
    )
    val both = forwardedFor("100.101.102.103, 192.168.1.2")
    assertEquals(
      decided(request(loopback, forwarding = both), twoProxies),
      Right(Trusted),
      "client 100.101.102.103, proxy1 192.168.1.2, proxy2 the loopback peer, both declared: the original client"
    )
    assertEquals(decided(request(loopback, forwarding = both), proxied), unauthenticated, "192.168.1.2 is the LAN")
    val strangerBehind = forwardedFor("203.0.113.9, 192.168.1.2")
    assertEquals(
      decided(request(loopback, forwarding = strangerBehind, presented = Presented.ApiKey), twoProxies),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
    )
    assertEquals(decided(request(front, forwarding = both), twoProxies), Right(Trusted), "from proxy1 directly")
    assertEquals(
      decided(request(loopback, forwarding = forwardedFor("127.0.0.1, ::1")), proxied),
      Right(Trusted),
      "a chain of proxies only: its leftmost, a client on this machine"
    )

  test("RFC 7239 Forwarded from a declared proxy: an IPv6 node with a port, unknown, hidden, several headers"):
    val v6policy = policyWith(List("127.0.0.0/8", "::1/128"))
    assertEquals(
      decided(request(loopback, forwarding = headers("Forwarded" -> "for=\"[2001:db8::1]:4711\"")), v6policy),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted)),
      "2001:db8::1 is listed nowhere"
    )
    assertEquals(
      decided(request(loopback, forwarding = headers("Forwarded" -> "for=\"[::1]:4711\"")), v6policy),
      Right(Trusted),
      "::1 is trusted: a chain of proxies only"
    )
    for hidden <- List("for=unknown", "for=_hidden", "for=_hidden, for=127.0.0.1") do
      assertEquals(decided(request(loopback, forwarding = headers("Forwarded" -> hidden)), v6policy), unauthenticated)
    val several = headers("Forwarded" -> "for=203.0.113.9", "Forwarded" -> "for=100.101.102.103;proto=https")
    assertEquals(decided(request(loopback, forwarding = several), v6policy), Right(Trusted))

  test("garbage, empty, overlong or conflicting reports from a declared proxy fail closed: the key, never trust"):
    for (label, forwarding) <- List(
        "garbage" -> forwardedFor("not-an-address"),
        "empty" -> forwardedFor(""),
        "empty entry" -> forwardedFor("100.101.102.103,"),
        "overlong" -> forwardedFor(List.fill(ForwardedChain.MaxHops + 1)("100.101.102.103").mkString(", ")),
        "open quote" -> headers("Forwarded" -> "for=\"100.101.102.103"),
        "empty Forwarded" -> headers("Forwarded" -> ""),
        "two X-Real-IP" -> headers("X-Real-IP" -> "100.101.102.103, 100.101.102.104"),
        "Forwarded against X-Forwarded-For" ->
          headers("Forwarded" -> "for=100.101.102.103", "X-Forwarded-For" -> "203.0.113.9"),
        "X-Forwarded-For against X-Real-IP" ->
          headers("X-Forwarded-For" -> "100.101.102.103", "X-Real-IP" -> "203.0.113.9")
      )
    do
      assertEquals(decided(request(loopback, forwarding = forwarding), proxied), unauthenticated, label)
      assertEquals(
        decided(request(loopback, forwarding = forwarding, presented = Presented.ApiKey), proxied),
        Right(KeyHolder),
        label
      )

  test("a forged X-Forwarded-For from a peer in no proxy network is refused, whatever address it names"):
    for
      (p, peers) <- List(
        policy -> List(loopback, tailnet, lan),
        proxied -> List(tailnet, lan),
        proxiedElsewhere -> List(loopback, tailnet, lan)
      )
      peer <- peers
      chain <- List("127.0.0.1", "100.101.102.103", "garbage")
    do assertEquals(decided(request(peer, forwarding = forwardedFor(chain)), p), undeclaredProxy, s"$peer $chain")

  test("an IPv4-mapped peer and an IPv4-mapped report match their IPv4 networks"):
    val mapped =
      java.net.Inet6Address.getByAddress(null, Array.fill[Byte](10)(0) ++ Array[Byte](-1, -1, 127, 0, 0, 1), -1)
    assertEquals(decided(request(mapped), policy), Right(Trusted))
    assertEquals(decided(request(mapped, forwarding = forwardedFor("100.101.102.103")), proxied), Right(Trusted))
    assertEquals(decided(request(mapped, forwarding = forwardedFor("::ffff:192.168.1.20")), proxied), unauthenticated)
    assertEquals(decided(request(mapped, forwarding = forwardedFor("100.101.102.103")), policy), undeclaredProxy)

  test("a proxy network lets nothing in by itself: its peer still needs a trusted or authenticated network"):
    val everywhere = policyWith(List("0.0.0.0/0", "::/0"))
    assertEquals(
      decided(request(stranger, forwarding = viaOnly, presented = Presented.ApiKey), everywhere),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
    )
    assertEquals(
      decided(request(refusedPeer, forwarding = forwardedFor("100.101.102.103")), everywhere),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
    )

  test("from a peer in no proxy network, a proxied request is refused (403) from any accepted network"):
    for
      p <- List(policy, proxiedElsewhere)
      peer <- List(loopback, tailnet, lan)
      presented <- Presented.values.toList
      path <- List("/api/health", "/", "/api/backups/x", "/mcp/agent")
      host <- List(Some("localhost:8000"), Some("rebound.example"), None)
    do
      assertEquals(
        decided(request(peer, path = path, host = host, presented = presented, forwarding = viaOnly), p),
        undeclaredProxy
      )
    // the local proxy's networks are loopback only: a tailnet peer that says it was forwarded is refused
    assertEquals(
      decided(request(tailnet, forwarding = viaOnly, presented = Presented.ApiKey), proxied),
      undeclaredProxy
    )
    // a refused or unlisted network keeps its own rule
    assertEquals(
      decided(request(refusedPeer, forwarding = viaOnly)),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
    )
    assertEquals(
      decided(request(stranger, forwarding = viaOnly)),
      Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
    )

  test("the headers that mark a request as forwarded: the standard one, the four de-facto ones and Via"):
    assertEquals(
      Forwarding.Headers,
      List("Forwarded", "X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Proto", "X-Real-IP", "Via")
    )
    Forwarding.Headers.foreach(name => assert(headers(name -> "x") != Forwarding.Absent, name))
    assertEquals(headers("X-Forwarded-Port" -> "443", "Forwarded-For" -> "1.2.3.4"), Forwarding.Absent)

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
