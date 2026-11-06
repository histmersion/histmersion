package com.histmersion.domain

import java.time.{Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter

/** The ONE outbound-HTTP policy (#1946): how every request the server makes to a third party — the acquisition APIs,
  * the media and event catalogues, the gazetteer downloads — identifies itself and what it does with a "slow down"
  * answer. Each client used to decide for itself (three User-Agents, one of them carrying the contact e-mail;
  * Retry-After honoured by one client of three; a JVM-wide redirect switch flipped by one importer). Pure.
  *
  *   - **Identity**: [[userAgent]] — the configured product string and nothing personal. An e-mail never travels in a
  *     header; where an API defines a contact parameter (Crossref's and OpenAlex's `mailto`, Unpaywall's `email`) the
  *     acquisition client passes the configured contact there, and only there.
  *   - **Slow down**: a 429 or a 502/503/504 is asked again after the answer's `Retry-After` (seconds or an HTTP date),
  *     else after 1, 2, 4 … s; at most `maxRetries` times, and a host that asks for more than `maxRetryAfterSeconds` at
  *     once is left alone for this request ([[decide]]).
  *   - **Redirects** are followed per client, never by flipping the JVM-wide default.
  *   - **Trust** (#1568): the JDK's roots, plus — only when configured (`acquire.tls`) — the operating system's and a
  *     PEM file's ([[trust]]). Roots are only ever ADDED: every certificate chain is still verified, and the host name
  *     checked, by the JDK; nothing here can switch that off.
  */
object HttpPolicy:

  /** What to do with an answer. */
  type Throttled = ThrottledValue
  type ThrottledValue = ThrottledValue.T
  object ThrottledValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserAgentResult = UserAgentResult.T
  object UserAgentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserAgentConfigured = UserAgentConfigured.T
  object UserAgentConfigured extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserAgentFallback = UserAgentFallback.T
  object UserAgentFallback extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMsResult = BackoffMsResult.T
  object BackoffMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BackoffMsAttempt = BackoffMsAttempt.T
  object BackoffMsAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterMsResult = RetryAfterMsResult.T
  object RetryAfterMsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetryAfterMsV = RetryAfterMsV.T
  object RetryAfterMsV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideCode = DecideCode.T
  object DecideCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideRetryAfter = DecideRetryAfter.T
  object DecideRetryAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideAttempt = _root_.com.histmersion.domain.HttpPolicy.BackoffMsAttempt
  val DecideAttempt: _root_.com.histmersion.domain.HttpPolicy.BackoffMsAttempt.type = _root_.com.histmersion.domain.HttpPolicy.BackoffMsAttempt
  type DecideMaxRetries = DecideMaxRetries.T
  object DecideMaxRetries extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideMaxRetryAfterSeconds = DecideMaxRetryAfterSeconds.T
  object DecideMaxRetryAfterSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecideSlowDown = DecideSlowDown.T
  object DecideSlowDown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MacKeychains = MacKeychainsValue
  type MacKeychainsValue = MacKeychainsValue.T
  object MacKeychainsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinuxBundles = LinuxBundlesValue
  type LinuxBundlesValue = LinuxBundlesValue.T
  object LinuxBundlesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrustSystemTrust = TrustSystemTrust.T
  object TrustSystemTrust extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrustExtraCaFile = TrustExtraCaFile.T
  object TrustExtraCaFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TrustOsName = TrustOsName.T
  object TrustOsName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PemCertificatesResult = PemCertificatesResult.T
  object PemCertificatesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PemCertificatesText = PemCertificatesText.T
  object PemCertificatesText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Step:
    case Accept
    case Wait(ms: Step.Wait.Ms, why: Step.Wait.Why)
    case GiveUp(why: Step.GiveUp.Why)

  object Step:
    object Wait:
      type Ms = Ms.T
      object Ms extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object GiveUp:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The answers that mean "try again later". */
  val Throttled: Set[com.histmersion.domain.HttpPolicy.Throttled] = Set(429, 502, 503, 504)

  /** The User-Agent of a configured product string, with anything that looks like an e-mail (and a `(mailto:…)`
    * comment) removed — so a setting that once carried a contact can never put it on the wire. A blank result falls
    * back to `fallback`.
    */
  def userAgent(configured: com.histmersion.domain.HttpPolicy.UserAgentConfigured, fallback: com.histmersion.domain.HttpPolicy.UserAgentFallback = "Histmersion"): com.histmersion.domain.HttpPolicy.UserAgentResult =
    val cleaned = Option(configured)
      .getOrElse("")
      .replaceAll("""\(\s*mailto:[^)]*\)""", "")
      .split("\\s+")
      .filterNot(_.contains("@"))
      .mkString(" ")
      .trim
    if cleaned.isEmpty then fallback else cleaned

  /** The wait before asking again after the `attempt`-th (0-based) slow-down: 1, 2, 4 … s. */
  def backoffMs(attempt: com.histmersion.domain.HttpPolicy.BackoffMsAttempt): com.histmersion.domain.HttpPolicy.BackoffMsResult = (1L << attempt.max(0).min(10)) * 1000L

  /** A `Retry-After` value — delay seconds or an HTTP date — as milliseconds from `now`. */
  def retryAfterMs(v: Option[com.histmersion.domain.HttpPolicy.RetryAfterMsV], now: Instant): Option[com.histmersion.domain.HttpPolicy.RetryAfterMsResult] =
    v.map(_.trim).filter(_.nonEmpty).flatMap { s =>
      s.toLongOption
        .map(_ * 1000L)
        .orElse(
          scala.util
            .Try(ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant)
            .toOption
            .map(t => (t.toEpochMilli - now.toEpochMilli).max(0L))
        )
    }

  /** The next step after the `attempt`-th (0-based) answer. `slowDown` says the answer asks to slow down although its
    * code does not (MediaWiki's `maxlag` comes back as a 200), named `why`.
    */
  def decide(
      code: com.histmersion.domain.HttpPolicy.DecideCode,
      retryAfter: Option[com.histmersion.domain.HttpPolicy.DecideRetryAfter],
      attempt: com.histmersion.domain.HttpPolicy.DecideAttempt,
      maxRetries: com.histmersion.domain.HttpPolicy.DecideMaxRetries,
      maxRetryAfterSeconds: com.histmersion.domain.HttpPolicy.DecideMaxRetryAfterSeconds,
      now: Instant,
      slowDown: Option[com.histmersion.domain.HttpPolicy.DecideSlowDown] = None
  ): Step =
    if Throttled(code) || slowDown.isDefined then
      val why = slowDown.getOrElse(s"HTTP $code")
      val wait = retryAfterMs(retryAfter, now).getOrElse(backoffMs(attempt)).max(0L)
      if attempt >= maxRetries then Step.GiveUp(s"$why after $maxRetries retries")
      else if wait > maxRetryAfterSeconds.toLong * 1000L then Step.GiveUp(s"$why: asked to wait ${wait / 1000} s")
      else Step.Wait(wait, why)
    else if code >= 400 then Step.GiveUp(s"HTTP $code")
    else Step.Accept

  // ── trust (#1568) ────────────────────────────────────────────────────────────────────────────────

  /** Where the roots an outbound TLS connection trusts come from. */
  enum Trust:
    /** The JDK's own `cacerts` — always. */
    case Jdk

    /** macOS keychains, exported as PEM by `security find-certificate -a -p <keychain>` (the JDK's `KeychainStore-ROOT`
      * reads the same roots, but through a JNI library the native image lacks).
      */
    case Keychains(paths: List[Trust.Keychains.Paths])

    /** The first of these PEM bundles that exists — a Linux distribution's CA bundle. */
    case Bundle(paths: List[Trust.Bundle.Paths])

    /** A PEM file the user named. */
    case PemFile(path: Trust.PemFile.Path)

  object Trust:
    object Keychains:
      type Paths = Paths.T
      object Paths extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Bundle:
      type Paths = Paths.T
      object Paths extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object PemFile:
      type Path = Path.T
      object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The macOS keychains that hold the system's roots: Apple's, and those an administrator added. */
  val MacKeychains: List[com.histmersion.domain.HttpPolicy.MacKeychains] =
    List("/System/Library/Keychains/SystemRootCertificates.keychain", "/Library/Keychains/System.keychain")

  /** Where Linux distributions keep their CA bundle (Debian/Ubuntu/Alpine, Fedora/RHEL, Arch/others). */
  val LinuxBundles: List[com.histmersion.domain.HttpPolicy.LinuxBundles] =
    List("/etc/ssl/certs/ca-certificates.crt", "/etc/pki/tls/certs/ca-bundle.crt", "/etc/ssl/cert.pem")

  /** The trust sources of a configuration on an OS (`os.name`): the JDK's always; the system's when `systemTrust`
    * (macOS keychains, else the Linux bundles); a PEM file when one is named.
    */
  def trust(systemTrust: com.histmersion.domain.HttpPolicy.TrustSystemTrust, extraCaFile: Option[com.histmersion.domain.HttpPolicy.TrustExtraCaFile], osName: com.histmersion.domain.HttpPolicy.TrustOsName): List[Trust] =
    val mac = osName.toLowerCase(java.util.Locale.ROOT).contains("mac")
    val system = Option.when(systemTrust)(if mac then Trust.Keychains(MacKeychains) else Trust.Bundle(LinuxBundles))
    Trust.Jdk :: system.toList ++ extraCaFile.map(Trust.PemFile(_)).toList

  private val PemCert =
    """(?s)-----BEGIN (?:TRUSTED |X509 )?CERTIFICATE-----(.*?)-----END (?:TRUSTED |X509 )?CERTIFICATE-----""".r

  /** The DER bytes of every certificate in a PEM text, in order; anything else (keys, comments, `security`'s chatter, a
    * block that is not base64) is skipped.
    */
  def pemCertificates(text: com.histmersion.domain.HttpPolicy.PemCertificatesText): List[com.histmersion.domain.HttpPolicy.PemCertificatesResult] =
    PemCert.findAllMatchIn(Option(text).getOrElse("")).toList.flatMap { m =>
      scala.util
        .Try(java.util.Base64.getMimeDecoder.decode(m.group(1).replaceAll("\\s+", "")))
        .toOption
        .filter(_.nonEmpty)
    }
