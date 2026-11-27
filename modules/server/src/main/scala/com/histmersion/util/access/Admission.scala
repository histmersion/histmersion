package com.histmersion
package util.access

import java.net.InetAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

import scala.jdk.CollectionConverters.*

import io.github.iltotore.iron.constraint.any.Pure

import com.histmersion.api.WebPaths
import com.histmersion.config.AuthConfig
import com.histmersion.util.problem.Problem
import com.histmersion.lint.{tolerated, LintRule}

/** How a request was let in. The wire spelling ([[wire]]) is what the gate tells the endpoints behind it. */
enum Admission(val wire: String) derives CanEqual:
  /** From a trusted network: it acts as the policy's principal with no credential. */
  case Trusted extends Admission("trusted")

  /** From an authenticated network, with the API key. */
  case KeyHolder extends Admission("key")

  /** From an authenticated network, with a live run token, to the one path an agent run reports on. */
  case RunToken extends Admission("run-token")

  /** To the agent MCP endpoint, which answers only to a run token of its own agent (checked behind this gate). */
  case Agent extends Admission("agent")

  /** Anonymous, to a page or a probe every client may read ([[Admission.isPublic]]). */
  case Public extends Admission("public")

object Admission:
  /** The request header the gate sets to the admission's [[wire]] spelling, after removing any the client sent. */
  val Header = "X-Histmersion-Admission"

  /** What a request carries that the decision reads. `port` is the port the server received it on; `forwarding` the
    * headers a reverse proxy marks what it forwards with, as the request carries them ([[ClientAddress]] reads them).
    */
  final case class Request(
      peer: InetAddress,
      port: AccessPolicy.Port,
      method: Method,
      path: Path,
      host: Option[HeaderText],
      origin: Option[HeaderText],
      referer: Option[HeaderText],
      forwarding: Forwarding,
      presented: Presented
  )

  /** A request's method, as far as the decision tells them apart. */
  enum Method derives CanEqual:
    /** GET and HEAD: they read. */
    case Read

    /** POST, PUT, PATCH and DELETE: they change something. */
    case Change

    /** Anything else (OPTIONS, TRACE, …). */
    case Other

  object Method:
    def of(name: String): Method = name.toUpperCase(java.util.Locale.ROOT) match
      case "GET" | "HEAD"                      => Read
      case "POST" | "PUT" | "PATCH" | "DELETE" => Change
      case _                                   => Other

  /** The path of a request, without its query. */
  type Path = Path.T
  object Path extends Newtype[String, Pure]:
    /** The path of a request target (`/api/x?y=1` → `/api/x`). */
    def ofTarget(uri: String): Path = Path(uri.takeWhile(c => c != '?' && c != '#'))

  /** A header's value as the request sent it. */
  type HeaderText = HeaderText.T
  object HeaderText extends Newtype[String, Pure]:
    def all(values: java.util.List[String]): List[HeaderText] = values.asScala.toList.map(HeaderText(_))

    def joined(values: List[HeaderText]): String = values.map(_.value).mkString(",")

  /** Where a request stands once its network, and for a forwarded one the client address its proxy reports, are read:
    * the address listed nowhere or refused, forwarded by an undeclared proxy, trusted, or held to the key.
    */
  private enum Standing derives CanEqual:
    case Unlisted, Refused, UndeclaredProxy, Trusted, NeedsKey

  private object Standing:
    def of(requirement: Option[AccessPolicy.Requirement]): Standing = requirement match
      case None                                         => Unlisted
      case Some(AccessPolicy.Requirement.Refused)       => Refused
      case Some(AccessPolicy.Requirement.Trusted)       => Trusted
      case Some(AccessPolicy.Requirement.Authenticated) => NeedsKey

  /** The credential a request presented, once the gate checked it. */
  enum Presented derives CanEqual:
    case Absent, ApiKey, RunToken, Wrong

  /** What a request presents as its `Authorization: Bearer …` value. */
  type Bearer = Bearer.T
  object Bearer extends Newtype[String, Pure]:
    /** The bearer value of an `Authorization` header, if it is one. */
    def of(header: String): Option[Bearer] =
      Option(header).map(_.trim).filter(_.startsWith("Bearer ")).map(h => Bearer(h.drop("Bearer ".length).trim))

    extension (b: Bearer)
      /** Whether `b` is `key`, compared in constant time: the SHA-256 of both, by `MessageDigest.isEqual`, so neither
        * the position of the first difference nor the key's length shows in the time it takes. An empty key matches
        * nothing.
        */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def matches(key: AuthConfig.ApiKey): Boolean =
        val expected = s"$key"
        expected.nonEmpty && MessageDigest.isEqual(sha256(s"$b"), sha256(expected))

    private def sha256(text: String): Array[Byte] = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8))

  /** The agent MCP endpoint and the path its runs report their plan quota on. */
  final case class AgentPaths(mcp: Path, quota: Path)

  /** What every client may read without a credential: the health and version probes, and the web UI's own page and
    * assets (the page asks for the key when the server refuses it the rest).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isPublic(method: Method, path: Path): Boolean =
    method == Method.Read && {
      val p = s"$path"
      p == "/api/health" || p == "/api/version" || p.startsWith("/web/") ||
      WebPaths.isApp(p.split('/').toList.filter(_.nonEmpty).map(WebPaths.Segment(_)))
    }

  /** Whether the request is let in, and how; or the problem that refuses it. In order: a peer of a refused network, or
    * of no listed network, is refused (403) whatever it sends — the public probes and the agent endpoint included; a
    * request a proxy forwarded, from a peer in no proxy network, is refused (403) the same way; from a peer in a proxy
    * network, the client address the proxy reports ([[ClientAddress]]) is then judged as a direct peer would be —
    * refused or unlisted (403), trusted, or authenticated — and one it reports no usable address for is held to the
    * key. Its `Host` must be one the server answers to (else 421); a change sent by a browser page must come from an
    * accepted origin (else 403); the agent endpoint is then left to its run-token gate; otherwise trusted, or
    * authenticated with the key (or a run token on the quota path), or a public read (else 401).
    */
  def decide(request: Request, policy: AccessPolicy, agent: AgentPaths): Either[Problem, Admission] =
    standingOf(request, policy) match
      case Standing.Unlisted        => Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkNotAccepted))
      case Standing.Refused         => Left(Problem.Refused.Policy(Problem.Refused.Rule.NetworkRefused))
      case Standing.UndeclaredProxy => Left(Problem.Refused.Policy(Problem.Refused.Rule.UndeclaredProxy))
      case admitted @ (Standing.Trusted | Standing.NeedsKey) => accepted(request, policy, agent, admitted)

  private def standingOf(request: Request, policy: AccessPolicy): Standing =
    policy.requirementOf(request.peer) match
      case None                                   => Standing.Unlisted
      case Some(AccessPolicy.Requirement.Refused) => Standing.Refused
      case peer @ Some(_)                         =>
        ClientAddress.of(request.peer, request.forwarding, policy) match
          case ClientAddress.Undeclared        => Standing.UndeclaredProxy
          case ClientAddress.Direct(_)         => Standing.of(peer)
          case ClientAddress.Forwarded(client) => Standing.of(policy.requirementOf(client))
          case ClientAddress.Unusable(_)       => Standing.NeedsKey

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def accepted(
      request: Request,
      policy: AccessPolicy,
      agent: AgentPaths,
      standing: Standing
  ): Either[Problem, Admission] =
    val host = request.host.map(h => AccessPolicy.HostName.ofHeader(s"$h"))
    if !host.exists(policy.answersTo) then Left(Problem.Refused.Misdirected(Problem.Raw(host.fold("")(h => s"$h"))))
    else if request.method == Method.Change && !sameSite(request, policy) then
      Left(Problem.Refused.Policy(Problem.Refused.Rule.CrossSite))
    else if s"${request.path}" == s"${agent.mcp}" || s"${request.path}".startsWith(s"${agent.mcp}/") then Right(Agent)
    else if standing == Standing.Trusted then Right(Trusted)
    else
      request.presented match
        case Presented.ApiKey                                  => Right(KeyHolder)
        case Presented.RunToken if request.path == agent.quota => Right(RunToken)
        case _ if isPublic(request.method, request.path)       => Right(Public)
        case _ => Left(Problem.Refused.Unauthenticated(Problem.Refused.Credential.ApiKey))

  /** A change's page is the server's own when its `Origin` (or, without one, its `Referer`) is an accepted origin. A
    * request with neither is no browser page's (the CLI, an MCP client) and carries no cookie a page could ride on, so
    * it passes; an origin that does not parse (`null`, from a sandboxed frame or a file) does not.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sameSite(request: Request, policy: AccessPolicy): Boolean =
    request.origin.orElse(request.referer) match
      case None       => true
      case Some(text) =>
        AccessPolicy.Origin.parse(s"$text").exists(policy.acceptsChangesFrom(_, request.port))
