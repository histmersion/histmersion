package com.histmersion.server

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.pluginhost.OutboundTls

import com.histmersion.config.{AppConfig, TlsConfig}
import com.histmersion.domain.HttpPolicy
import com.histmersion.domain.library.acquisition.SttpHttp

import java.nio.file.{Files, Path}
import java.security.cert.{CertificateFactory, CertPathValidator, PKIXParameters, TrustAnchor, X509Certificate}
import scala.jdk.CollectionConverters.*

/** `acquire.tls` (#1568): more roots than the JDK's, never less verification. The fixtures are public CA certificates:
  * HARICA TLS RSA Root CA 2021 (not in the JDK's cacerts) and GEANT TLS RSA 1, the intermediate
  * archiv.ub.uni-heidelberg.de chains through to it.
  */
class OutboundTlsSuite extends munit.FunSuite:

  private def resource(name: String): String =
    new String(getClass.getResourceAsStream(s"/tls/$name").readAllBytes(), "US-ASCII")

  private val rootPem = resource("harica-tls-rsa-root-2021.pem")
  private val intermediatePem = resource("geant-tls-rsa-1.pem")

  private def subject(c: X509Certificate) = c.getSubjectX500Principal.getName

  /** Does `cert` chain to one of the trust manager's roots? (PKIX, no revocation: offline.) */
  private def validates(roots: Array[X509Certificate], cert: X509Certificate): Boolean =
    val params = new PKIXParameters(roots.map(r => new TrustAnchor(r, null)).toSet.asJava)
    params.setRevocationEnabled(false)
    val path = CertificateFactory.getInstance("X.509").generateCertPath(List(cert).asJava)
    scala.util.Try(CertPathValidator.getInstance("PKIX").validate(path, params)).isSuccess

  test("PEM blocks parse into certificates; junk around them is skipped"):
    val cs = OutboundTls.certificates("keychain: \"/x\"\n" + rootPem + "\nnot a cert\n" + intermediatePem)
    assertEquals(
      cs.map(subject).map(_.takeWhile(_ != ',')),
      List("CN=HARICA TLS RSA Root CA 2021", "CN=GEANT TLS RSA 1")
    )
    assertEquals(OutboundTls.certificates(com.histmersion.domain.HttpPolicy.PemText("-----BEGIN CERTIFICATE-----\nnot base64!\n-----END CERTIFICATE-----")), Nil)

  test("the merged store keeps every JDK root and adds the extra one; the chain then validates"):
    val root = OutboundTls.certificates(rootPem).head
    val inter = OutboundTls.certificates(intermediatePem).head
    val jdk = OutboundTls.trustManager(Nil).getAcceptedIssuers
    val more = OutboundTls.trustManager(List(root)).getAcceptedIssuers
    assert(!jdk.exists(_ == root), "the premise: the JDK does not ship HARICA TLS RSA Root CA 2021")
    assertEquals(more.length, jdk.length + 1)
    assert(jdk.forall(c => more.contains(c)), "no JDK root is lost")
    assert(!validates(jdk, inter), "with the JDK's roots the intermediate does not chain")
    assert(validates(more, inter), "with the added root it does")

  private val keychainOut = rootPem
  private def fakeRun(seen: collection.mutable.Buffer[List[String]]): OutboundTls.Run = cmd =>
    seen += cmd
    Right(keychainOut)

  test("system trust on macOS reads the system keychains with `security`"):
    val seen = collection.mutable.Buffer.empty[List[String]]
    val got = OutboundTls.configure(TlsConfig(systemTrust = com.histmersion.config.TlsConfig.SystemTrust(true), extraCaFile = com.histmersion.config.TlsConfig.ExtraCaFile("")), com.histmersion.domain.HttpPolicy.OsName("Mac OS X"), fakeRun(seen))
    val (_, line) = got.getOrElse(fail("configured"))
    assertEquals(seen.headOption.map(_.take(4)), Some(List("/usr/bin/security", "find-certificate", "-a", "-p")))
    assert(line.contains("macOS system keychains: 1 root(s)"), line)

  test("an extra PEM file adds its roots; an unreadable one is named and the JDK's roots stay"):
    val dir = Files.createTempDirectory("tls")
    val pem = dir.resolve("extra.pem")
    Files.writeString(pem, rootPem)
    val (_, ok) = OutboundTls.configure(TlsConfig(com.histmersion.config.TlsConfig.SystemTrust(false), pem.toString), com.histmersion.domain.HttpPolicy.OsName("Linux")).getOrElse(fail("configured"))
    assert(ok.contains(s"$pem: 1 root(s)"), ok)
    val missing = dir.resolve("nope.pem").toString
    val (_, bad) = OutboundTls.configure(TlsConfig(com.histmersion.config.TlsConfig.SystemTrust(false), missing), com.histmersion.domain.HttpPolicy.OsName("Linux")).getOrElse(fail("configured"))
    assert(bad.contains(s"$missing: NOT used"), bad)
    val empty = dir.resolve("empty.pem")
    Files.writeString(empty, "nothing here")
    val (_, none) = OutboundTls.configure(TlsConfig(com.histmersion.config.TlsConfig.SystemTrust(false), empty.toString), com.histmersion.domain.HttpPolicy.OsName("Linux")).getOrElse(fail("configured"))
    assert(none.contains("holds no PEM certificate"), none)

  test("off by default: no custom context, the HTTP client keeps its own"):
    val cfg = AppConfig.load().acquire.tls
    assertEquals((cfg.systemTrust, cfg.extraCa), (false, None))
    assertEquals(OutboundTls.configure(cfg), None)

  test("a Linux bundle that is missing is reported, not fatal"):
    val got = OutboundTls.roots(HttpPolicy.Trust.Bundle(List(com.histmersion.domain.HttpPolicy.Trust.Bundle.Paths("/nonexistent/ca.crt"))))
    assert(got.isLeft)

  test("live: archiv.ub.uni-heidelberg.de with the system's roots (HISTMERSION_LIVE_APIS=1, macOS)"):
    assume(
      sys.env.get("HISTMERSION_LIVE_APIS").contains("1") &&
        System.getProperty("os.name", "").toLowerCase.contains("mac"),
      "live, macOS only"
    )
    val cfg = AppConfig.load().acquire
    val plain = SttpHttp(cfg, tries = com.histmersion.pluginhost.PolicyHttp.Tries(1), clock = () => java.time.Instant.now(java.time.Clock.systemUTC())).bytes(com.histmersion.domain.library.acquisition.Http.Url("https://archiv.ub.uni-heidelberg.de/"), Map.empty)
    assert(plain.isLeft, s"premise: with the JDK's roots alone the handshake fails, got $plain")
    val tls = OutboundTls.configure(cfg.tls.modify(_.systemTrust).setTo(true)).map(_._1)
    val fixed = SttpHttp(cfg, tries = com.histmersion.pluginhost.PolicyHttp.Tries(1), tls = tls, clock = () => java.time.Instant.now(java.time.Clock.systemUTC())).bytes(com.histmersion.domain.library.acquisition.Http.Url("https://archiv.ub.uni-heidelberg.de/"), Map.empty)
    assert(fixed.isRight, s"with the system's roots: $fixed")
