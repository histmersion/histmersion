package com.histmersion
package domain

import java.time.{Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

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
@movesTo(FeaturePlace.LibraryAcquisition)
object HttpPolicy:
  type MaxRetries = MaxRetries.T
  object MaxRetries extends Newtype[Int, Pure]
  type MaxRetryAfterSeconds = MaxRetryAfterSeconds.T
  object MaxRetryAfterSeconds extends Newtype[Int, Pure]
  type OsName = OsName.T
  object OsName extends Newtype[String, Pure]
  type PemCertificate = PemCertificate.T
  object PemCertificate extends Newtype[Array[Byte], Pure]
  type PemText = PemText.T
  object PemText extends Newtype[String, Pure]
  type RetryAfter = RetryAfter.T
  object RetryAfter extends Newtype[String, Pure]
  type SlowDown = SlowDown.T
  object SlowDown extends Newtype[String, Pure]
  type StatusCode = StatusCode.T
  object StatusCode extends Newtype[Int, Pure]
  type SystemTrust = SystemTrust.T
  object SystemTrust extends Newtype[Boolean, Pure]
  type UserAgent = UserAgent.T
  object UserAgent extends Newtype[String, Pure]

  /** The number of the answer a step follows, counted from 0. */
  type Attempt = Attempt.T
  object Attempt extends Newtype[Int, Pure]

  /** What to do with an answer. */
  enum Step:
    case Accept
    case Wait(ms: Step.Wait.Ms, why: Rationale)
    case GiveUp(why: Rationale)

  /** Why the policy waits or gives up: the account of its step, for the log, not a failure (the request's failure is
    * the answer's own).
    */
  enum Rationale:
    /** The answer's status `code` asks to slow down (429, 502, 503, 504). */
    case SlowDownStatus(code: StatusCode)

    /** The answer asks to slow down although its code does not, in its own word (MediaWiki's `maxlag`). */
    case SlowedDown(word: SlowDown)

    /** As `why`, after `retries` retries. */
    case Retried(why: Rationale, retries: MaxRetries)

    /** As `why`, and asked to wait `asked`, longer than the policy waits at once. */
    case WaitTooLong(why: Rationale, asked: Step.Wait.Ms)

    /** The answer's status `code` is an error that is not asked again. */
    case Failed(code: StatusCode)

    /** The account as the log shows it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case SlowDownStatus(code)    => s"HTTP $code"
      case SlowedDown(word)        => s"$word"
      case Retried(why, retries)   => s"${why.said} after $retries retries"
      case WaitTooLong(why, asked) => s"${why.said}: asked to wait ${asked.seconds} s"
      case Failed(code)            => s"HTTP $code"

  object Step:
    object Wait:
      type Ms = Ms.T
      object Ms extends Newtype[Long, Pure]:
        extension (ms: Ms)
          /** The wait in whole seconds, rounded down. */
          def seconds: Long = ms.value / 1000L

  /** The answers that mean "try again later". */
  val Throttled: Set[StatusCode] = Set(StatusCode(429), StatusCode(502), StatusCode(503), StatusCode(504))

  /** The User-Agent of a configured product string, with anything that looks like an e-mail (and a `(mailto:…)`
    * comment) removed — so a setting that once carried a contact can never put it on the wire. A blank result falls
    * back to `fallback`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def userAgent(configured: UserAgent, fallback: UserAgent = UserAgent("Histmersion")): UserAgent =
    val cleaned = Option(configured.value)
      .getOrElse("")
      .replaceAll("""\(\s*mailto:[^)]*\)""", "")
      .split("\\s+")
      .filterNot(_.contains("@"))
      .mkString(" ")
      .trim
    if cleaned.isEmpty then fallback else UserAgent(cleaned)

  /** The wait before asking again after the `attempt`-th (0-based) slow-down: 1, 2, 4 … s. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def backoffMs(attempt: Attempt): Step.Wait.Ms = Step.Wait.Ms((1L << attempt.value.max(0).min(10)) * 1000L)

  /** A `Retry-After` value — delay seconds or an HTTP date — as milliseconds from `now`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def retryAfterMs(v: Option[RetryAfter], now: Instant): Option[Step.Wait.Ms] =
    v.map(_.value.trim)
      .filter(_.nonEmpty)
      .flatMap { s =>
        s.toLongOption
          .map(_ * 1000L)
          .orElse(
            scala.util
              .Try(ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant)
              .toOption
              .map(t => (t.toEpochMilli - now.toEpochMilli).max(0L))
          )
      }
      .map(Step.Wait.Ms(_))

  /** The next step after the `attempt`-th (0-based) answer. `slowDown` says the answer asks to slow down although its
    * code does not (MediaWiki's `maxlag` comes back as a 200), named `why`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def decide(
      code: StatusCode,
      retryAfter: Option[RetryAfter],
      attempt: Attempt,
      maxRetries: MaxRetries,
      maxRetryAfterSeconds: MaxRetryAfterSeconds,
      now: Instant,
      slowDown: Option[SlowDown] = None
  ): Step =
    if Throttled(code) || slowDown.isDefined then
      val why = slowDown.fold(Rationale.SlowDownStatus(code))(Rationale.SlowedDown(_))
      val wait = retryAfterMs(retryAfter, now).getOrElse(backoffMs(attempt)).value.max(0L)
      if attempt.value >= maxRetries.value then Step.GiveUp(Rationale.Retried(why, maxRetries))
      else if wait > maxRetryAfterSeconds.value.toLong * 1000L then
        Step.GiveUp(Rationale.WaitTooLong(why, Step.Wait.Ms(wait)))
      else Step.Wait(Step.Wait.Ms(wait), why)
    else if code.value >= 400 then Step.GiveUp(Rationale.Failed(code))
    else Step.Accept

  // ── trust (#1568) ────────────────────────────────────────────────────────────────────────────────

  /** Where the roots an outbound TLS connection trusts come from. */
  enum Trust derives CanEqual:
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
      object Paths extends Newtype[String, Pure]
    object Bundle:
      type Paths = Paths.T
      object Paths extends Newtype[String, Pure]
    object PemFile:
      type Path = Path.T
      object Path extends Newtype[String, Pure]

  /** The macOS keychains that hold the system's roots: Apple's, and those an administrator added. */
  val MacKeychains: List[Trust.Keychains.Paths] =
    List(
      Trust.Keychains.Paths("/System/Library/Keychains/SystemRootCertificates.keychain"),
      Trust.Keychains.Paths("/Library/Keychains/System.keychain")
    )

  /** Where Linux distributions keep their CA bundle (Debian/Ubuntu/Alpine, Fedora/RHEL, Arch/others). */
  val LinuxBundles: List[Trust.Bundle.Paths] =
    List(
      Trust.Bundle.Paths("/etc/ssl/certs/ca-certificates.crt"),
      Trust.Bundle.Paths("/etc/pki/tls/certs/ca-bundle.crt"),
      Trust.Bundle.Paths("/etc/ssl/cert.pem")
    )

  /** The trust sources of a configuration on an OS (`os.name`): the JDK's always; the system's when `systemTrust`
    * (macOS keychains, else the Linux bundles); a PEM file when one is named.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def trust(systemTrust: SystemTrust, extraCaFile: Option[Trust.PemFile.Path], osName: OsName): List[Trust] =
    val mac = osName.value.toLowerCase(java.util.Locale.ROOT).contains("mac")
    val system =
      Option.when(systemTrust.value)(if mac then Trust.Keychains(MacKeychains) else Trust.Bundle(LinuxBundles))
    Trust.Jdk :: system.toList ++ extraCaFile.map(Trust.PemFile(_)).toList

  private val PemCert =
    """(?s)-----BEGIN (?:TRUSTED |X509 )?CERTIFICATE-----(.*?)-----END (?:TRUSTED |X509 )?CERTIFICATE-----""".r

  /** The DER bytes of every certificate in a PEM text, in order; anything else (keys, comments, `security`'s chatter, a
    * block that is not base64) is skipped.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pemCertificates(text: PemText): List[PemCertificate] =
    PemCert.findAllMatchIn(Option(text.value).getOrElse("")).toList.flatMap { m =>
      scala.util
        .Try(java.util.Base64.getMimeDecoder.decode(m.group(1).replaceAll("\\s+", "")))
        .toOption
        .filter(_.nonEmpty)
        .map(PemCertificate(_))
    }
