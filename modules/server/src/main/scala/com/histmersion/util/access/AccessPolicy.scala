package com.histmersion
package util.access

import java.net.{InetAddress, URI}
import java.util.Locale

import scala.util.Try

import com.histmersion.config.{AuthConfig, ServerConfig}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.lint.{tolerated, LintRule}

/** Who the server lets in, read from the `auth` settings once, when the listener is bound: each listed network and what
  * a request from it gets, the names a request may give as its `Host`, the page origins that may send changes besides
  * the server's own, and the networks a reverse proxy in front of it connects from.
  */
final case class AccessPolicy private (
    networks: List[AccessPolicy.Network],
    hosts: Set[AccessPolicy.HostName],
    origins: Set[AccessPolicy.Origin],
    trustedAs: AccessPolicy.Principal,
    proxies: List[Cidr]
):
  import AccessPolicy.*

  /** The policy in one line for the log: each network and what it gets, the proxy networks, and the names answered to.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def summary: String =
    val listed =
      if networks.isEmpty then "no network accepted"
      else networks.map(n => s"${n.cidr} ${n.requires.toString.toLowerCase(Locale.ROOT)}").mkString(", ")
    val proxied =
      if proxies.isEmpty then ""
      else s"; forwarded requests from ${proxies.mkString(", ")} judged by the client address they report"
    s"$listed$proxied; answers to ${hosts.toList.map(h => s"$h").sorted.mkString(", ")}"

  /** What a request from `peer` gets: the requirement of the longest listed prefix that holds it, if any does. */
  def requirementOf(peer: InetAddress): Option[Requirement] =
    networks.filter(_.cidr.holds(peer)).maxByOption(_.cidr.prefix).map(_.requires)

  /** Whether `address` is a declared proxy's: it is in a proxy network. A forwarded request is read only from such a
    * peer, and the hops of the chain it reports that are such addresses are passed over ([[ForwardedChain]]).
    */
  def proxiedFrom(address: InetAddress): Boolean = proxies.exists(_.holds(address))

  /** Whether a request may give `host` (a `Host` header, its port ignored) as the name it asked for. */
  def answersTo(host: HostName): Boolean = hosts.contains(host)

  /** Whether a page at `origin` may send changes to the server listening on `port`: one of the names it answers to, at
    * that port, over HTTP or HTTPS, or one of the listed origins.
    */
  def acceptsChangesFrom(origin: Origin, port: Port): Boolean =
    origins
      .contains(origin) || (answersTo(origin.host) && origin.port == port && Origin.Schemes.contains(origin.scheme))

  /** Whether listening on every address of `binds` leaves a network open, each address checked on its own: one it can
    * be reached from that no listed network covers, or one only authenticated networks cover while no key is set. Each
    * open address is reported once, by the first range it is reached from that is open ([[reachOf]]).
    */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def exposure(binds: NonEmptyVector[ServerConfig.Host], keySet: Boolean): Either[Problems, Unit] =
    Problems.from(binds.toVector.flatMap(b => exposureOf(b, keySet).left.toOption)).toLeft(())

  /** Whether listening on `bind` leaves a network open ([[exposure]] of one address). */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def exposureOf(bind: ServerConfig.Host, keySet: Boolean): Either[Problem, Unit] =
    reachOf(bind).iterator
      .flatMap(range => uncovered(range, keySet).map(range -> _))
      .nextOption()
      .fold[Either[Problem, Unit]](Right(())) { case (range, why) =>
        Left(Problem.Config.Exposed(Problem.Raw(s"$bind"), Problem.Raw(s"$range"), why))
      }

  /** Whether a public URL of `publicUrl` needs a declared proxy that is missing: it is HTTPS, which this server does
    * not serve itself, so a reverse proxy forwards to it; when loopback (where a proxy on this machine connects from)
    * is trusted and no proxy network covers it, a proxy that does not mark what it forwards would hand every client the
    * owner's trust. Loopback is covered when a proxy network holds `127.0.0.1` or `::1`, as it is trusted when a
    * trusted network holds either.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def proxyNeeded(publicUrl: ServerConfig.PublicUrl): Either[Problem, Unit] =
    val https = Origin.parse(s"$publicUrl").exists(_.scheme == "https")
    val loopbackTrusted = LoopbackPeers.exists(a => requirementOf(a).contains(Requirement.Trusted))
    if https && loopbackTrusted && !LoopbackPeers.exists(proxiedFrom) then
      Left(Problem.Config.ProxyUndeclared(Problem.Raw(s"$publicUrl")))
    else Right(())

  /** The first reason `range` is open: an address no network covers, or one only an authenticated network covers with
    * no key. Walks the listed networks' boundaries inside the range, so it is exact, not sampled.
    */
  private def uncovered(range: Cidr, keySet: Boolean): Option[Problem.Config.Exposure] =
    val inside = networks.filter(n => n.cidr.family == range.family)
    val cuts = (inside.flatMap(n => List(n.cidr.first, n.cidr.last + 1)) :+ range.first)
      .filter(c => c >= range.first && c <= range.last)
      .distinct
      .sorted
    cuts.iterator
      .flatMap { at =>
        inside.filter(n => n.cidr.first <= at && at <= n.cidr.last).maxByOption(_.cidr.prefix) match
          case None                                                          => Some(Problem.Config.Exposure.Unlisted)
          case Some(n) if n.requires == Requirement.Authenticated && !keySet => Some(Problem.Config.Exposure.NoKey)
          case Some(_)                                                       => None
      }
      .nextOption()

object AccessPolicy:
  /** What a request from a listed network gets. */
  enum Requirement derives CanEqual:
    /** In, with nothing to show: it acts as the policy's [[AccessPolicy.trustedAs]]. */
    case Trusted

    /** In with a valid credential (the API key), for reads and writes alike. */
    case Authenticated

    /** Never in, whatever it carries. */
    case Refused

  /** Who a trusted request acts as. */
  enum Principal derives CanEqual:
    /** The person the install belongs to. */
    case Owner

  /** A listed network and what a request from it gets. */
  final case class Network(cidr: Cidr, requires: Requirement)

  /** A host name as a `Host` header or an origin gives it: lower case, an IPv6 address in brackets, no trailing dot. */
  type HostName = HostName.T
  object HostName extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
    /** The name a `Host` header asks for, without its port. */
    def ofHeader(header: String): HostName =
      val h = header.trim
      val name = if h.startsWith("[") then h.takeWhile(_ != ']') + "]" else h.takeWhile(_ != ':')
      of(name)

    /** A name written in a setting or a URL: an IPv6 address gets its brackets. */
    def of(name: String): HostName =
      val n = name.trim.toLowerCase(Locale.ROOT).stripSuffix(".")
      HostName(if n.contains(':') && !n.startsWith("[") then s"[$n]" else n)

  /** A TCP port. */
  type Port = Port.T
  object Port extends Newtype[Int, io.github.iltotore.iron.constraint.any.Pure]

  /** The addresses a listener on `bind` can be reached from. Loopback is reached from loopback only (`localhost` from
    * both families'); an address of one family from that whole family (the operating system routes to it from any
    * interface); either wildcard, and a name that is no literal address, from everywhere. `0.0.0.0` too: the JDK opens
    * its server sockets dual-stack and binds the IPv4 wildcard as `::`, so IPv6 clients reach it as well.
    */
  def reachOf(bind: ServerConfig.Host): List[Cidr] =
    if isLocalhost(bind) then Cidr.loopback
    else
      Cidr.literal(bindText(bind)) match
        case Some(a) if a.isLoopbackAddress =>
          Cidr.loopback.filter(_.family == Cidr.numbered(a)._1)
        case Some(a) if a.isAnyLocalAddress => Cidr.everything
        case Some(a)                        =>
          Cidr.everything.filter(_.family == Cidr.numbered(a)._1)
        case None => Cidr.everything

  /** Every address `binds` lists besides a wildcard (`0.0.0.0` or `::`, the first one listed), each paired with it: a
    * wildcard already listens on every address of the port, so a second listener on it fails to bind (Linux refuses an
    * address under a wildcard bound on its port) or takes that address's connections from the wildcard's (macOS lets
    * it). `0.0.0.0` and `::1` overlap too: the JDK binds `0.0.0.0` as the dual-stack `::`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def overlaps(binds: NonEmptyVector[ServerConfig.Host]): Vector[Problem] =
    val all = binds.toVector.zipWithIndex
    all.find((b, _) => isWildcard(b)).toVector.flatMap { (wildcard, at) =>
      all.collect {
        case (b, i) if i != at => Problem.Config.WildcardOverlap(Problem.Raw(s"$wildcard"), Problem.Raw(s"$b"))
      }
    }

  /** Every address `binds` lists again after its first entry, however it is written ([[sameAddress]]): a second
    * listener cannot bind what the first holds. Each repeat is paired with the first entry, both as written.
    */
  def repeats(binds: NonEmptyVector[ServerConfig.Host]): Vector[Problem] =
    val all = binds.toVector
    all.zipWithIndex.flatMap { (b, i) =>
      all
        .take(i)
        .find(sameAddress(_, b))
        .map(first => Problem.Config.BindRepeated(Problem.Raw.of(first), Problem.Raw.of(b)))
    }

  /** `binds` with each address once, at its first entry ([[repeats]] names the others). */
  def once(binds: NonEmptyVector[ServerConfig.Host]): NonEmptyVector[ServerConfig.Host] =
    NonEmptyVector.of(
      binds.head,
      binds.tail.zipWithIndex.collect {
        case (b, i) if !binds.toVector.take(i + 1).exists(sameAddress(_, b)) => b
      }*
    )

  /** Whether two bind entries name one address: two literals of one number (`::1`, `[::1]` and `0:0:0:0:0:0:0:1`; an
    * IPv4-mapped `::ffff:127.0.0.1` and `127.0.0.1`), or one name in any case, with or without its trailing dot. No
    * name is looked up: `localhost` and `127.0.0.1` are two entries.
    */
  def sameAddress(a: ServerConfig.Host, b: ServerConfig.Host): Boolean =
    def address(bind: ServerConfig.Host): Either[String, (Cidr.Family, BigInt)] =
      Cidr.literal(bindText(bind)).map(Cidr.numbered).toRight(bindText(bind).toLowerCase(Locale.ROOT).stripSuffix("."))
    address(a) == address(b)

  /** A page origin: scheme, host and port (the scheme's default port when the text names none). */
  final case class Origin(scheme: String, host: HostName, port: Port)

  object Origin:
    val Schemes: Set[String] = Set("http", "https")

    /** The origin of an `Origin` header, a `Referer` or a URL setting; `None` for `null` and anything unparsable. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(text: String): Option[Origin] =
      Try(URI(text.trim)).toOption.flatMap { u =>
        for
          scheme <- Option(u.getScheme).map(_.toLowerCase(Locale.ROOT))
          host <- Option(u.getHost).filter(_.nonEmpty)
          port = if u.getPort >= 0 then u.getPort else if scheme == "https" then 443 else 80
        yield Origin(scheme, HostName.of(host), Port(port))
      }

  /** The names every install answers to: the loopback ones. */
  val LoopbackNames: Set[HostName] = Set(HostName("localhost"), HostName("127.0.0.1"), HostName("[::1]"))

  /** The addresses a proxy on this machine connects from. */
  private val LoopbackPeers: List[InetAddress] = List("127.0.0.1", "::1").flatMap(Cidr.literal)

  /** The policy of a listener bound to every address of `server.host`, or every reason it must not start: a setting
    * that is wrong, an address listed twice ([[repeats]]), a wildcard listed beside another address ([[overlaps]]), an
    * address that leaves a network open ([[exposure]]), or an HTTPS public URL in front of a trusted loopback no proxy
    * network covers ([[proxyNeeded]]). A repeated address is checked once, at its first entry.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def forListener(auth: AuthConfig, server: ServerConfig): Either[Problems, AccessPolicy] =
    from(auth, server).flatMap { p =>
      val binds = once(server.host)
      val open = p.exposure(binds, s"${auth.apiKey}".nonEmpty).left.toOption.toVector.flatMap(_.toVector)
      val unproxied = p.proxyNeeded(server.publicUrl).left.toOption
      Problems.from(repeats(server.host) ++ overlaps(binds) ++ open ++ unproxied).toLeft(p)
    }

  /** The policy the `auth` settings and the listener's own (its bind addresses, its public URL) describe, or every
    * setting that is wrong. One network named in two lists contradicts itself: the later list is named, against the
    * earlier (trusted, authenticated, refused).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def from(auth: AuthConfig, server: ServerConfig): Either[Problems, AccessPolicy] =
    val lists = List(
      (TrustedKey, auth.trustedNetworks, Requirement.Trusted),
      (AuthenticatedKey, auth.authenticatedNetworks, Requirement.Authenticated),
      (RefusedKey, auth.refusedNetworks, Requirement.Refused)
    ).map((key, raw, requires) => key -> networks(key, raw.map(n => s"$n"), requires))
    val read = lists.map((key, parsed) => key -> parsed.toOption.toList.flatten)
    val clashes = read.zipWithIndex.flatMap { case ((key, mine), i) =>
      read.take(i).collectFirst {
        case (earlier, theirs) if mine.exists(n => theirs.exists(_.cidr == n.cidr)) =>
          Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Conflicts(Problem.SettingKey(earlier)))
      }
    }
    val origins = auth.origins.map(o => s"$o").map { raw =>
      Origin.parse(raw).filter(o => Origin.Schemes.contains(o.scheme)).toRight(malformed(OriginsKey, raw))
    }
    val principal = s"${auth.trustedAs}".trim.toLowerCase(Locale.ROOT) match
      case "owner" => Right(Principal.Owner)
      case other   => Left(unknown(TrustedAsKey, other))
    // a proxy network may overlap any list, the trusted one above all (a proxy on this machine): it says only where a
    // forwarded request may come from, not what it gets
    val proxies =
      auth.proxyNetworks.map(n => s"$n").map(raw => Cidr.parse(raw).toRight(malformed(ProxyNetworksKey, raw)))
    val problems: Vector[Problem] =
      lists.toVector.flatMap(_._2.left.toOption.toVector.flatten) ++ clashes ++
        origins.collect { case Left(p) => p } ++ principal.left.toOption ++ proxies.collect { case Left(p) => p }
    Problems.from(problems) match
      case Some(all) => Left(all)
      case None      =>
        val names = LoopbackNames ++ auth.hosts.map(h => HostName.of(s"$h")) ++
          server.host.toVector.filterNot(isWildcard).map(b => HostName.of(bindText(b))) ++
          Origin.parse(s"${server.publicUrl}").map(_.host)
        val publicOrigin = Origin.parse(s"${server.publicUrl}").toSet
        Right(
          AccessPolicy(
            read.flatMap(_._2),
            names,
            origins.collect { case Right(o) => o }.toSet ++ publicOrigin,
            principal.getOrElse(Principal.Owner),
            proxies.collect { case Right(c) => c }
          )
        )

  private val TrustedKey = "auth.trusted-networks"
  private val AuthenticatedKey = "auth.authenticated-networks"
  private val RefusedKey = "auth.refused-networks"
  private val TrustedAsKey = "auth.trusted-as"
  private val ProxyNetworksKey = "auth.proxy-networks"
  private val OriginsKey = "auth.origins"

  private def networks(key: String, raw: List[String], requires: Requirement): Either[Vector[Problem], List[Network]] =
    val read = raw.map(r => Cidr.parse(r).map(Network(_, requires)).toRight(malformed(key, r)))
    val bad = read.collect { case Left(p) => p }.toVector
    if bad.nonEmpty then Left(bad) else Right(read.collect { case Right(n) => n })

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def malformed(key: String, raw: String): Problem =
    Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Malformed(Problem.Raw(raw)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unknown(key: String, raw: String): Problem =
    Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Unknown(Problem.Raw(raw)))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def bindText(bind: ServerConfig.Host): String = s"$bind".trim.stripPrefix("[").stripSuffix("]")

  private def isWildcard(bind: ServerConfig.Host): Boolean =
    Cidr.literal(bindText(bind)).exists(_.isAnyLocalAddress) || bindText(bind).isEmpty

  private def isLocalhost(bind: ServerConfig.Host): Boolean = bindText(bind).toLowerCase(Locale.ROOT) == "localhost"
