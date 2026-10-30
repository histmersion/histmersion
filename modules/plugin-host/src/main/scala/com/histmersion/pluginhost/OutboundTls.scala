package com.histmersion.pluginhost

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

/** The roots an acquisition request trusts (#1568, [[HttpPolicy.trust]]): the JDK's, and — only when `acquire.tls` asks
  * — the operating system's and a PEM file's, merged into ONE trust store behind the JDK's own PKIX trust manager. So a
  * chain is verified exactly as before (path, validity, revocation settings, host name — the HTTP client still checks
  * that), just against more roots.
  *
  * Why not the JDK's `KeychainStore-ROOT`: on the JVM it reads the macOS roots, but in the native image its JNI library
  * (`libosxsecurity`, which needs `libjava`) is not there — `UnsatisfiedLinkError` (measured with GraalVM 25.0.1).
  * `security find-certificate -a -p` prints the same roots as PEM and works in both.
  */
object OutboundTls:

  /** A command → its standard output (the seam a test replaces). */
  type Run = List[String] => Either[String, String]

  /** Runs a command, waiting at most 30 s. */
  val run: Run = cmd =>
    try
      val p = new ProcessBuilder(cmd*).redirectErrorStream(false).start()
      val out = Using.resource(p.getInputStream)(in => new String(in.readAllBytes(), StandardCharsets.UTF_8))
      if !p.waitFor(30, TimeUnit.SECONDS) then
        p.destroyForcibly()
        Left(s"${cmd.mkString(" ")} did not finish")
      else if p.exitValue != 0 then Left(s"${cmd.mkString(" ")} exited ${p.exitValue}")
      else Right(out)
    catch case e: Exception => Left(s"${cmd.headOption.getOrElse("")}: ${e.getMessage}")

  /** A file's text (the seam a test replaces). */
  type Read = Path => Either[String, String]

  val read: Read = p =>
    if !Files.isRegularFile(p) then Left(s"$p is not a file")
    else
      try Right(Files.readString(p, StandardCharsets.ISO_8859_1)) // PEM is ASCII; never fail on a stray byte
      catch case e: Exception => Left(s"$p: ${e.getMessage}")

  /** The X.509 certificates in a PEM text; a block that does not parse is skipped. */
  def certificates(pem: String): List[X509Certificate] =
    val cf = CertificateFactory.getInstance("X.509")
    HttpPolicy
      .pemCertificates(pem)
      .flatMap(der =>
        scala.util.Try(cf.generateCertificate(new ByteArrayInputStream(der))).toOption.collect {
          case c: X509Certificate => c
        }
      )

  /** The roots one source adds (none for [[HttpPolicy.Trust.Jdk]], which is the base). */
  def roots(t: HttpPolicy.Trust, run: Run = run, read: Read = read): Either[String, List[X509Certificate]] = t match
    case HttpPolicy.Trust.Jdk              => Right(Nil)
    case HttpPolicy.Trust.Keychains(paths) =>
      val found = paths.filter(p => Files.exists(Path.of(p)))
      if found.isEmpty then Left(s"none of the keychains ${paths.mkString(", ")} exists")
      else run(List("/usr/bin/security", "find-certificate", "-a", "-p") ++ found).map(certificates)
    case HttpPolicy.Trust.Bundle(paths) =>
      paths
        .map(Path.of(_))
        .find(Files.isRegularFile(_))
        .toRight(s"no CA bundle at ${paths.mkString(", ")}")
        .flatMap(read)
        .map(certificates)
    case HttpPolicy.Trust.PemFile(path) =>
      read(Path.of(path)).flatMap { text =>
        val cs = certificates(text)
        if cs.isEmpty then Left(s"$path holds no PEM certificate") else Right(cs)
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
  def configure(
      cfg: TlsConfig,
      osName: String = System.getProperty("os.name", ""),
      run: Run = run,
      read: Read = read
  ): Option[(SSLContext, String)] =
    Option.when(cfg.custom) {
      val sources = HttpPolicy.trust(cfg.systemTrust, cfg.extraCa, osName).filterNot(_ == HttpPolicy.Trust.Jdk)
      val got = sources.map(s => s -> roots(s, run, read))
      val extra = got.flatMap(_._2.toOption).flatten
      val notes = got.map {
        case (s, Right(cs)) => s"${describe(s)}: ${cs.size} root(s)"
        case (s, Left(e))   => s"${describe(s)}: NOT used ($e)"
      }
      context(extra) -> s"acquire TLS trusts the JDK's roots + ${notes.mkString("; ")}"
    }

  private def describe(t: HttpPolicy.Trust): String = t match
    case HttpPolicy.Trust.Jdk          => "the JDK"
    case HttpPolicy.Trust.Keychains(_) => "the macOS system keychains"
    case HttpPolicy.Trust.Bundle(_)    => "the system CA bundle"
    case HttpPolicy.Trust.PemFile(p)   => p
