package com.histmersion
package util.access

import java.net.{InetAddress, URI}
import java.util.Locale

import scala.util.Try

import com.histmersion.config.{AuthConfig, ServerConfig}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.{Problem, Problems}

/** Who the server lets in, read from the `auth` settings once, when the listener is bound: each listed network and what
  * a request from it gets, the names a request may give as its `Host`, the page origins that may send changes besides
  * the server's own, and the reverse proxy declared in front of it.
  */
final case class AccessPolicy private (
    networks: List[AccessPolicy.Network],
    hosts: Set[AccessPolicy.HostName],
    origins: Set[AccessPolicy.Origin],
    trustedAs: AccessPolicy.Principal,
    proxy: AccessPolicy.Proxy
):
  import AccessPolicy.*

  /** The policy in one line for the log: each network and what it gets, the proxy, and the names answered to. */
  def summary: String =
    val listed =
      if networks.isEmpty then "no network accepted"
      else networks.map(n => s"${n.cidr} ${n.requires.toString.toLowerCase(Locale.ROOT)}").mkString(", ")
    val proxied = proxy match
      case Proxy.Undeclared => ""
      case Proxy.Local      => "; a local proxy's forwarded requests authenticated"
    s"$listed$proxied; answers to ${hosts.toList.map(h => s"$h").sorted.mkString(", ")}"

  /** What a request from `peer` gets: the requirement of the longest listed prefix that holds it, if any does. */
  def requirementOf(peer: InetAddress): Option[Requirement] =
    networks.filter(_.cidr.holds(peer)).maxByOption(_.cidr.prefix).map(_.requires)

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
  def exposure(binds: NonEmptyVector[ServerConfig.Host], keySet: Boolean): Either[Problems, Unit] =
    Problems.from(binds.toVector.flatMap(b => exposureOf(b, keySet).left.toOption)).toLeft(())

  /** Whether listening on `bind` leaves a network open ([[exposure]] of one address). */
  def exposureOf(bind: ServerConfig.Host, keySet: Boolean): Either[Problem, Unit] =
    reachOf(bind).iterator
      .flatMap(range => uncovered(range, keySet).map(range -> _))
      .nextOption()
      .fold[Either[Problem, Unit]](Right(())) { case (range, why) =>
        Left(Problem.Config.Exposed(Problem.Raw(s"$bind"), Problem.Raw(s"$range"), why))
      }

  /** Whether a public URL of `publicUrl` needs a declared proxy that is missing: it is HTTPS, which this server does
    * not serve itself, so a reverse proxy forwards to it; when loopback (where a proxy on this machine connects from)
    * is trusted, a proxy that does not mark what it forwards would hand every client the owner's trust.
    */
  def proxyNeeded(publicUrl: ServerConfig.PublicUrl): Either[Problem, Unit] =
    val https = Origin.parse(s"$publicUrl").exists(_.scheme == "https")
    val loopbackTrusted = LoopbackPeers.exists(a => requirementOf(a).contains(Requirement.Trusted))
    if https && loopbackTrusted && proxy == Proxy.Undeclared then
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

  /** The reverse proxy in front of the server. */
  enum Proxy derives CanEqual:
    /** None is declared (`none`). */
    case Undeclared

    /** One on this machine (`local`): it connects over loopback and marks every request it forwards with `Forwarded` or
      * `X-Forwarded-For`, so a loopback request so marked is held to the authenticated rules.
      */
    case Local

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
    * interface); the dual-stack wildcard `::`, and a name that is no literal address, from everywhere.
    */
  def reachOf(bind: ServerConfig.Host): List[Cidr] =
    if isLocalhost(bind) then Cidr.loopback
    else
      Cidr.literal(bindText(bind)) match
        case Some(a) if a.isLoopbackAddress =>
          Cidr.loopback.filter(_.family == Cidr.numbered(a)._1)
        case Some(a) if a.isAnyLocalAddress && Cidr.numbered(a)._1 == Cidr.Family.V6 => Cidr.everything
        case Some(a)                                                                 =>
          Cidr.everything.filter(_.family == Cidr.numbered(a)._1)
        case None => Cidr.everything

  /** A page origin: scheme, host and port (the scheme's default port when the text names none). */
  final case class Origin(scheme: String, host: HostName, port: Port)

  object Origin:
    val Schemes: Set[String] = Set("http", "https")

    /** The origin of an `Origin` header, a `Referer` or a URL setting; `None` for `null` and anything unparsable. */
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
    * that is wrong, an address that leaves a network open ([[exposure]]), or an HTTPS public URL in front of a trusted
    * loopback with no proxy declared ([[proxyNeeded]]).
    */
  def forListener(auth: AuthConfig, server: ServerConfig): Either[Problems, AccessPolicy] =
    from(auth, server).flatMap { p =>
      val open = p.exposure(server.host, s"${auth.apiKey}".nonEmpty).left.toOption.toVector.flatMap(_.toVector)
      val unproxied = p.proxyNeeded(server.publicUrl).left.toOption
      Problems.from(open ++ unproxied).toLeft(p)
    }

  /** The policy the `auth` settings and the listener's own (its bind addresses, its public URL) describe, or every
    * setting that is wrong. One network named in two lists contradicts itself: the later list is named, against the
    * earlier (trusted, authenticated, refused).
    */
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
    val proxy = s"${auth.proxy}".trim.toLowerCase(Locale.ROOT) match
      case "none"  => Right(Proxy.Undeclared)
      case "local" => Right(Proxy.Local)
      case other   => Left(unknown(ProxyKey, other))
    val problems: Vector[Problem] =
      lists.toVector.flatMap(_._2.left.toOption.toVector.flatten) ++ clashes ++
        origins.collect { case Left(p) => p } ++ principal.left.toOption ++ proxy.left.toOption
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
            proxy.getOrElse(Proxy.Undeclared)
          )
        )

  private val TrustedKey = "auth.trusted-networks"
  private val AuthenticatedKey = "auth.authenticated-networks"
  private val RefusedKey = "auth.refused-networks"
  private val TrustedAsKey = "auth.trusted-as"
  private val ProxyKey = "auth.proxy"
  private val OriginsKey = "auth.origins"

  private def networks(key: String, raw: List[String], requires: Requirement): Either[Vector[Problem], List[Network]] =
    val read = raw.map(r => Cidr.parse(r).map(Network(_, requires)).toRight(malformed(key, r)))
    val bad = read.collect { case Left(p) => p }.toVector
    if bad.nonEmpty then Left(bad) else Right(read.collect { case Right(n) => n })

  private def malformed(key: String, raw: String): Problem =
    Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Malformed(Problem.Raw(raw)))

  private def unknown(key: String, raw: String): Problem =
    Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Unknown(Problem.Raw(raw)))

  private def bindText(bind: ServerConfig.Host): String = s"$bind".trim.stripPrefix("[").stripSuffix("]")

  private def isWildcard(bind: ServerConfig.Host): Boolean =
    Cidr.literal(bindText(bind)).exists(_.isAnyLocalAddress) || bindText(bind).isEmpty

  private def isLocalhost(bind: ServerConfig.Host): Boolean = bindText(bind).toLowerCase(Locale.ROOT) == "localhost"
