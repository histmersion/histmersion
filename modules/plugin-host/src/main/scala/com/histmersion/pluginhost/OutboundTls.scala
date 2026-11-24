package com.histmersion
package pluginhost
import com.histmersion.util.problem.{External, Problem, Problems}
import com.histmersion.util.problem.Text.sentences

import com.histmersion.domain.HttpPolicy
import com.histmersion.config.TlsConfig

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.KeyStore
import java.security.cert.{CertificateFactory, X509Certificate}
import java.util.concurrent.TimeUnit
import javax.net.ssl.{SSLContext, TrustManagerFactory, X509TrustManager}
import scala.util.Using
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The roots an acquisition request trusts (#1568, [[HttpPolicy.trust]]): the JDK's, and — only when `acquire.tls` asks
  * — the operating system's and a PEM file's, merged into ONE trust store behind the JDK's own PKIX trust manager. So a
  * chain is verified exactly as before (path, validity, revocation settings, host name — the HTTP client still checks
  * that), just against more roots.
  *
  * Why not the JDK's `KeychainStore-ROOT`: on the JVM it reads the macOS roots, but in the native image its JNI library
  * (`libosxsecurity`, which needs `libjava`) is not there — `UnsatisfiedLinkError` (measured with GraalVM 25.0.1).
  * `security find-certificate -a -p` prints the same roots as PEM and works in both.
  */
@atBoundary(BoundaryKind.Process, "builds the TLS trust store from the JDK, the OS keychain and a PEM file")
object OutboundTls:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]

  /** A command → its standard output (the seam a test replaces). */

  type Run = List[String] => Either[Problem, String]

  /** How long a command may run. */
  private val Wait = 30L

  /** Runs a command, waiting at most 30 s. */
  val run: Run = cmd =>
    val tool = Problem.Io.Tool(cmd.headOption.getOrElse(""))
    try
      val p = new ProcessBuilder(cmd*).redirectErrorStream(false).start()
      val out = Using.resource(p.getInputStream)(in => new String(in.readAllBytes(), StandardCharsets.UTF_8))
      if !p.waitFor(Wait, TimeUnit.SECONDS) then
        p.destroyForcibly()
        Left(Problem.Io.Process(tool, Problem.Io.Ending.TimedOut(Problem.Millis(TimeUnit.SECONDS.toMillis(Wait)))))
      else if p.exitValue != 0 then
        val err = Using.resource(p.getErrorStream)(in => new String(in.readAllBytes(), StandardCharsets.UTF_8))
        Left(
          Problem.Io.Process(
            tool,
            Problem.Io.Ending.Exited(Problem.Io.ExitCode(p.exitValue), External.Stderr.tail(err))
          )
        )
      else Right(out)
    catch case e: Exception => Left(Problem.Io.Process(tool, Problem.Io.Ending.NotStarted(External.Reported.of(e))))

  /** A file's text (the seam a test replaces). */
  type Read = Path => Either[Problem, String]

  val read: Read = p =>
    if !Files.isRegularFile(p) then Left(Problem.Io.Wrong(Problem.Io.Path(s"$p"), Problem.Io.Kind.File))
    else
      try Right(Files.readString(p, StandardCharsets.ISO_8859_1)) // PEM is ASCII; never fail on a stray byte
      catch
        case e: Exception =>
          Left(Problem.Io.File(Problem.Io.Access.Read, Problem.Io.Path(s"$p"), External.Reported.of(e)))

  /** Whether a keychain file is there (the seam a test replaces: the macOS keychains exist on no other system). */
  type Present = Path => Boolean

  val present: Present = Files.exists(_)

  /** None of `paths` is there: each one (an empty list names no path). */
  private def noneOf(paths: List[String]): Problems =
    val missing = (if paths.isEmpty then List("") else paths).map(p =>
      Problem.Io.Wrong(Problem.Io.Path(p), Problem.Io.Kind.Present)
    )
    Problems.of(missing.head, missing.tail*)

  /** The X.509 certificates in a PEM text; a block that does not parse is skipped. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def certificates(pem: HttpPolicy.PemText): List[X509Certificate] =
    val cf = CertificateFactory.getInstance("X.509")
    HttpPolicy
      .pemCertificates(pem)
      .flatMap(der =>
        scala.util.Try(cf.generateCertificate(new ByteArrayInputStream(der.value))).toOption.collect {
          case c: X509Certificate => c
        }
      )

  /** The roots one source adds (none for [[HttpPolicy.Trust.Jdk]], which is the base). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def roots(
      t: HttpPolicy.Trust,
      run: Run = run,
      read: Read = read,
      present: Present = present
  ): Either[Problems, List[X509Certificate]] = t match
    case HttpPolicy.Trust.Jdk              => Right(Nil)
    case HttpPolicy.Trust.Keychains(paths) =>
      val found = paths.filter(p => present(Path.of(p.value)))
      if found.isEmpty then Left(noneOf(paths.map(p => s"$p")))
      else
        run(List("/usr/bin/security", "find-certificate", "-a", "-p") ++ found.map(_.value))
          .map(text => certificates(HttpPolicy.PemText(text)))
    case HttpPolicy.Trust.Bundle(paths) =>
      paths
        .map(p => Path.of(p.value))
        .find(Files.isRegularFile(_))
        .toRight(noneOf(paths.map(p => s"$p")))
        .flatMap(read(_))
        .map(text => certificates(HttpPolicy.PemText(text)))
    case HttpPolicy.Trust.PemFile(path) =>
      read(Path.of(path.value)).flatMap { text =>
        val cs = certificates(HttpPolicy.PemText(text))
        if cs.isEmpty then
          Left(Problem.Io.Corrupt(Problem.Io.Format.Text, Problem.Where(s"$path"), Problem.Io.Damage.Undecodable))
        else Right(cs)
      }

  /** The JDK's default trust manager. */
  private def jdkTrust(): X509TrustManager =
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(null: KeyStore)
    tmf.getTrustManagers
      .collectFirst { case t: X509TrustManager => t }
      .getOrElse(throw new IllegalStateException("the JDK has no X509 trust manager"))

  /** The JDK's PKIX trust manager over ONE store: the JDK's roots plus `extra`. */
  def trustManager(extra: List[X509Certificate]): X509TrustManager =
    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    (jdkTrust().getAcceptedIssuers.toList ++ extra)
      .distinctBy(_.getEncoded.toSeq)
      .zipWithIndex
      .foreach((c, i) => ks.setCertificateEntry(s"ca-$i", c))
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ks)
    tmf.getTrustManagers
      .collectFirst { case t: X509TrustManager => t }
      .getOrElse(throw new IllegalStateException("no X509 trust manager over the merged store"))

  /** An SSL context trusting the JDK's roots plus `extra` ([[trustManager]]). */
  def context(extra: List[X509Certificate]): SSLContext =
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, Array(trustManager(extra)), null)
    ctx

  /** What a configuration gives: `None` when it asks for nothing beyond the JDK (the HTTP client keeps its default),
    * else the context and a line for the log. A source that cannot be read is named in the line and left out — a broken
    * opt-in never takes the JDK's own roots away.
    */
  @tolerated(LintRule.Global, "pending G15 (no globals; everything from config)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def configure(
      cfg: TlsConfig,
      osName: HttpPolicy.OsName = HttpPolicy.OsName(System.getProperty("os.name", "")),
      run: Run = run,
      read: Read = read,
      present: Present = present
  ): Option[(SSLContext, Summary)] =
    Option.when(cfg.custom) {
      val sources = HttpPolicy
        .trust(
          HttpPolicy.SystemTrust(cfg.systemTrust.value),
          cfg.extraCa.map(f => HttpPolicy.Trust.PemFile.Path(f.value)),
          osName
        )
        .filterNot(_ == HttpPolicy.Trust.Jdk)
      val got = sources.map(s => s -> roots(s, run, read, present))
      val extra = got.flatMap(_._2.toOption).flatten
      val notes = got.map {
        case (s, Right(cs)) => s"${describe(s)}: ${cs.size} root(s)"
        case (s, Left(e))   => s"${describe(s)}: NOT used (${e.sentences.mkString("; ")})"
      }
      context(extra) -> Summary(s"acquire TLS trusts the JDK's roots + ${notes.mkString("; ")}")
    }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def describe(t: HttpPolicy.Trust): Summary = t match
    case HttpPolicy.Trust.Jdk          => Summary("the JDK")
    case HttpPolicy.Trust.Keychains(_) => Summary("the macOS system keychains")
    case HttpPolicy.Trust.Bundle(_)    => Summary("the system CA bundle")
    case HttpPolicy.Trust.PemFile(p)   => Summary(p.value)
