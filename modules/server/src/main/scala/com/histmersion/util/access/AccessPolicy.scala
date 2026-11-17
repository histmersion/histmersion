package com.histmersion
package util.access

import java.net.{InetAddress, URI}
import java.util.Locale

import scala.util.Try

import com.histmersion.config.{AuthConfig, ServerConfig}
import com.histmersion.util.problem.{Problem, Problems}

/** Who the server lets in, read from the `auth` settings once, when the listener is bound: each accepted network and
  * what a request from it needs, the names a request may give as its `Host`, and the page origins that may send changes
  * besides the server's own.
  */
final case class AccessPolicy private (
    networks: List[AccessPolicy.Network],
    hosts: Set[AccessPolicy.HostName],
    origins: Set[AccessPolicy.Origin],
    trustedAs: AccessPolicy.Principal
):
  import AccessPolicy.*

  /** The policy in one line for the log: each network and what it needs, and the names answered to. */
  def summary: String =
    val listed =
      if networks.isEmpty then "no network accepted"
      else networks.map(n => s"${n.cidr} ${n.requires.toString.toLowerCase(Locale.ROOT)}").mkString(", ")
    s"$listed; answers to ${hosts.toList.map(h => s"$h").sorted.mkString(", ")}"

  /** What a request from `peer` needs: the requirement of the longest listed prefix that holds it, if any does. */
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

  /** Whether listening on `bind` leaves a network open: one the bind can be reached from that no listed network covers,
    * or one only authenticated networks cover while no key is set. Each such network is reported by the range the bind
    * is reached from ([[reachOf]]).
    */
  def exposure(bind: ServerConfig.Host, keySet: Boolean): Either[Problem, Unit] =
    reachOf(bind).iterator
      .flatMap(range => uncovered(range, keySet).map(range -> _))
      .nextOption()
      .fold[Either[Problem, Unit]](Right(())) { case (range, why) =>
        Left(Problem.Config.Exposed(Problem.Raw(s"$bind"), Problem.Raw(s"$range"), why))
      }

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
  /** What a request from a network needs. */
  enum Requirement derives CanEqual:
    /** Nothing: it acts as the policy's [[AccessPolicy.trustedAs]]. */
    case Trusted

    /** A valid credential (the API key), for reads and writes alike. */
    case Authenticated

  /** Who a trusted request acts as. */
  enum Principal derives CanEqual:
    /** The person the install belongs to. */
    case Owner

  /** A listed network and what a request from it needs. */
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

  /** The policy of a listener bound to `server.host`, or why it must not start: a setting that is wrong, or a network
    * the bind leaves open ([[exposure]]).
    */
  def forListener(auth: AuthConfig, server: ServerConfig): Either[Problems, AccessPolicy] =
    from(auth, server).flatMap(p => p.exposure(server.host, s"${auth.apiKey}".nonEmpty).map(_ => p))

  /** The policy the `auth` settings and the listener's own (its bind address, its public URL) describe, or every
    * setting that is wrong.
    */
  def from(auth: AuthConfig, server: ServerConfig): Either[Problems, AccessPolicy] =
    val trusted = networks(TrustedKey, auth.trustedNetworks.map(n => s"$n"), Requirement.Trusted)
    val authenticated =
      networks(AuthenticatedKey, auth.authenticatedNetworks.map(n => s"$n"), Requirement.Authenticated)
    val both = trusted.toOption.toList.flatten.map(_.cidr).toSet
    val clash = authenticated.toOption.toList.flatten.find(n => both.contains(n.cidr)).map { _ =>
      Problem.Config.Setting(Problem.SettingKey(AuthenticatedKey), Problem.Config.Fault.Conflicts(TrustedKeySetting))
    }
    val origins = auth.origins.map(o => s"$o").map { raw =>
      Origin.parse(raw).filter(o => Origin.Schemes.contains(o.scheme)).toRight(malformed(OriginsKey, raw))
    }
    val principal = s"${auth.trustedAs}".trim.toLowerCase(Locale.ROOT) match
      case "owner" => Right(Principal.Owner)
      case other   =>
        Left(Problem.Config.Setting(Problem.SettingKey(TrustedAsKey), Problem.Config.Fault.Unknown(Problem.Raw(other))))
    val problems: Vector[Problem] =
      trusted.left.toOption.toVector.flatten ++ authenticated.left.toOption.toVector.flatten ++ clash ++
        origins.collect { case Left(p) => p } ++ principal.left.toOption
    Problems.from(problems) match
      case Some(all) => Left(all)
      case None      =>
        val names = LoopbackNames ++ auth.hosts.map(h => HostName.of(s"$h")) ++
          Option.when(!isWildcard(server.host))(HostName.of(bindText(server.host))) ++
          Origin.parse(s"${server.publicUrl}").map(_.host)
        val publicOrigin = Origin.parse(s"${server.publicUrl}").toSet
        Right(
          AccessPolicy(
            trusted.getOrElse(Nil) ++ authenticated.getOrElse(Nil),
            names,
            origins.collect { case Right(o) => o }.toSet ++ publicOrigin,
            principal.getOrElse(Principal.Owner)
          )
        )

  private val TrustedKey = "auth.trusted-networks"
  private val TrustedKeySetting = Problem.SettingKey(TrustedKey)
  private val AuthenticatedKey = "auth.authenticated-networks"
  private val TrustedAsKey = "auth.trusted-as"
  private val OriginsKey = "auth.origins"

  private def networks(key: String, raw: List[String], requires: Requirement): Either[Vector[Problem], List[Network]] =
    val read = raw.map(r => Cidr.parse(r).map(Network(_, requires)).toRight(malformed(key, r)))
    val bad = read.collect { case Left(p) => p }.toVector
    if bad.nonEmpty then Left(bad) else Right(read.collect { case Right(n) => n })

  private def malformed(key: String, raw: String): Problem =
    Problem.Config.Setting(Problem.SettingKey(key), Problem.Config.Fault.Malformed(Problem.Raw(raw)))

  private def bindText(bind: ServerConfig.Host): String = s"$bind".trim.stripPrefix("[").stripSuffix("]")

  private def isWildcard(bind: ServerConfig.Host): Boolean =
    Cidr.literal(bindText(bind)).exists(_.isAnyLocalAddress) || bindText(bind).isEmpty

  private def isLocalhost(bind: ServerConfig.Host): Boolean = bindText(bind).toLowerCase(Locale.ROOT) == "localhost"
