package com.histmersion
package util.access

import java.net.InetAddress

import scala.jdk.CollectionConverters.*

import org.ekrich.config.ConfigFactory

import com.histmersion.config.AppConfig
import com.histmersion.util.access.ClientAddress.Fault
import com.histmersion.util.access.ForwardedChain.Hop

class ForwardedChainSuite extends munit.FunSuite:

  private def policyWith(proxyNetworks: String*): AccessPolicy =
    val listed = proxyNetworks.map(n => s"\"$n\"").mkString("[", ", ", "]")
    val cfg = AppConfig
      .loadWithUserText(ConfigFactory.empty, s"histmersion.auth.proxy-networks = $listed")
      .fold(e => fail(s"$e"), _.config)
    AccessPolicy.from(cfg.auth, cfg.server).fold(e => fail(s"$e"), identity)

  private val loopbackProxy = policyWith("127.0.0.0/8", "::1/128")

  private def at(text: String): InetAddress = Cidr.literal(text).getOrElse(fail(s"no literal: $text"))
  private def known(text: String): Either[Fault, Hop] = Right(Hop.Address(at(text)))
  private def texts(values: String*): List[Admission.HeaderText] = Admission.HeaderText.all(values.asJava)

  private def hops(source: Option[Either[Fault, ForwardedChain]]): Either[Fault, Vector[Hop]] =
    source.getOrElse(fail("no chain")).map(_.hops)

  test("a node: an IPv4 or IPv6 address, with or without a port, quoted or not"):
    assertEquals(ForwardedChain.node("192.0.2.60"), known("192.0.2.60"))
    assertEquals(ForwardedChain.node(" 192.0.2.60:5678 "), known("192.0.2.60"))
    assertEquals(ForwardedChain.node("2001:db8::1"), known("2001:db8::1"))
    assertEquals(ForwardedChain.node("[2001:db8::1]"), known("2001:db8::1"))
    assertEquals(ForwardedChain.node("[2001:db8::1]:4711"), known("2001:db8::1"))
    assertEquals(ForwardedChain.node("\"[2001:db8::1]:4711\""), known("2001:db8::1"))
    assertEquals(ForwardedChain.node("\"[::1]:80\""), known("::1"))
    assertEquals(ForwardedChain.node("\"192.0.2.60:_port\""), known("192.0.2.60"))
    assertEquals(ForwardedChain.node("::ffff:127.0.0.1"), known("127.0.0.1"))
    assertEquals(ForwardedChain.node("[::ffff:100.101.102.103]:443"), known("100.101.102.103"))

  test("a node: RFC 7239's unknown and obfuscated identifiers name no usable address"):
    List("unknown", "UNKNOWN", "\"unknown\"", "_hidden", "_SEVKISEK", "\"_x.y-z\"")
      .foreach(t => assertEquals(ForwardedChain.node(t), Right(Hop.Hidden), t))

  test("a node: anything else is malformed, and no name is ever looked up"):
    List(
      "",
      "  ",
      "localhost",
      "example.com",
      "300.1.1.1",
      "1.2.3",
      "1.2.3.4:",
      "1.2.3.4:99999",
      "1.2.3.4:http",
      "1.2.3.4:5:6",
      "[2001:db8::1",
      "[2001:db8::1]:",
      "[2001:db8::1]x",
      "[1.2.3.4]",
      "fe80::1%en0",
      "_",
      "_bad/char",
      "\"192.0.2.60",
      "192.0.2.60\"",
      "\"a\"b\"",
      "1.2.3.4\\",
      "garbage"
    ).foreach(t => assertEquals(ForwardedChain.node(t), Left(Fault.Malformed), t))

  test("X-Forwarded-For: a comma list, several header lines read as one, in order"):
    assertEquals(
      hops(ForwardedChain.ofForwardedFor(texts("203.0.113.9, 192.168.1.20:5000", "127.0.0.1"))),
      Right(Vector(at("203.0.113.9"), at("192.168.1.20"), at("127.0.0.1")).map(Hop.Address(_)))
    )
    assertEquals(ForwardedChain.ofForwardedFor(Nil), None)
    List(
      texts(""),
      texts(" "),
      texts("203.0.113.9,"),
      texts("203.0.113.9,,127.0.0.1"),
      texts("garbage, 127.0.0.1"),
      texts("203.0.113.9", "")
    ).foreach(v => assertEquals(hops(ForwardedChain.ofForwardedFor(v)), Left(Fault.Malformed), s"$v"))

  test("X-Forwarded-For: more hops than the cap is too long"):
    val many = List.fill(ForwardedChain.MaxHops + 1)("192.0.2.1").mkString(", ")
    assertEquals(hops(ForwardedChain.ofForwardedFor(texts(many))), Left(Fault.TooLong))
    val most = List.fill(ForwardedChain.MaxHops)("192.0.2.1").mkString(", ")
    assertEquals(hops(ForwardedChain.ofForwardedFor(texts(most))).map(_.size), Right(ForwardedChain.MaxHops))

  test("Forwarded: the for= of each element, quoted IPv6 with a port, other parameters ignored"):
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("for=192.0.2.60;proto=http;by=203.0.113.43"))),
      Right(Vector(Hop.Address(at("192.0.2.60"))))
    )
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("For=\"[2001:db8:cafe::17]:4711\""))),
      Right(Vector(Hop.Address(at("2001:db8:cafe::17"))))
    )
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("for=192.0.2.43, for=198.51.100.17;by=203.0.113.60;proto=http;host=x"))),
      Right(Vector(Hop.Address(at("192.0.2.43")), Hop.Address(at("198.51.100.17"))))
    )

  test("Forwarded: several header lines are one list, in order"):
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("for=192.0.2.43", "for=\"[2001:db8::1]:4711\";proto=https"))),
      Right(Vector(Hop.Address(at("192.0.2.43")), Hop.Address(at("2001:db8::1"))))
    )

  test("Forwarded: unknown, an obfuscated node, and an element without for= are hidden hops"):
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("for=unknown, for=_hidden, proto=https, for=127.0.0.1"))),
      Right(Vector(Hop.Hidden, Hop.Hidden, Hop.Hidden, Hop.Address(at("127.0.0.1"))))
    )

  test("Forwarded: a quoted value may hold the separators; a header with no for= at all gives no chain"):
    assertEquals(
      hops(ForwardedChain.ofForwarded(texts("host=\"a,b;c\";for=192.0.2.60"))),
      Right(Vector(Hop.Address(at("192.0.2.60"))))
    )
    assertEquals(ForwardedChain.ofForwarded(texts("proto=https;host=example.org")), None)
    assertEquals(ForwardedChain.ofForwarded(Nil), None)

  test("Forwarded: garbage, an empty value, an open quote or for= twice in one element is malformed"):
    List(
      texts(""),
      texts("for=192.0.2.60,"),
      texts("for=192.0.2.60;for=192.0.2.61"),
      texts("for=\"192.0.2.60"),
      texts("for"),
      texts("=192.0.2.60"),
      texts("f r=192.0.2.60"),
      texts("for=192.0.2.60", ""),
      texts("for=nonsense"),
      texts("for=")
    ).foreach(v => assertEquals(hops(ForwardedChain.ofForwarded(v)), Left(Fault.Malformed), s"$v"))

  test("X-Real-IP: one address in one header line; anything else is malformed"):
    assertEquals(hops(ForwardedChain.ofRealIp(texts("100.101.102.103"))), Right(Vector(Hop.Address(at("100.101.102.103")))))
    assertEquals(hops(ForwardedChain.ofRealIp(texts("unknown"))), Right(Vector(Hop.Hidden)))
    assertEquals(ForwardedChain.ofRealIp(Nil), None)
    List(texts("1.2.3.4, 5.6.7.8"), texts("1.2.3.4", "5.6.7.8"), texts(""), texts("junk"))
      .foreach(v => assertEquals(hops(ForwardedChain.ofRealIp(v)), Left(Fault.Malformed), s"$v"))

  test("the sources in precedence order: Forwarded, X-Forwarded-For, X-Real-IP; each only when present"):
    val marked = new Forwarding.Marked(texts("for=192.0.2.1"), texts("192.0.2.2"), texts("192.0.2.3"))
    assertEquals(
      ForwardedChain.sources(marked).map(_.map(_.hops)),
      List("192.0.2.1", "192.0.2.2", "192.0.2.3").map(a => Right(Vector(Hop.Address(at(a)))))
    )
    assertEquals(ForwardedChain.sources(new Forwarding.Marked(Nil, Nil, Nil)), Nil)
    assertEquals(ForwardedChain.sources(new Forwarding.Marked(Nil, texts("192.0.2.2"), Nil)).size, 1)

  test("the walk: from the right, past every declared proxy, to the first address that is none"):
    def client(chain: String, policy: AccessPolicy = loopbackProxy) =
      hops(ForwardedChain.ofForwardedFor(texts(chain))).flatMap(h => ForwardedChain.from(h)).flatMap(_.client(policy))
    assertEquals(client("100.101.102.103"), Right(at("100.101.102.103")))
    assertEquals(client("203.0.113.9, 100.101.102.103"), Right(at("100.101.102.103")), "a forged entry is never reached")
    assertEquals(client("100.101.102.103, 127.0.0.1, ::1"), Right(at("100.101.102.103")))
    assertEquals(client("127.0.0.1, ::1"), Right(at("127.0.0.1")), "all proxies: the leftmost")
    assertEquals(client("::ffff:127.0.0.1, 100.101.102.103"), Right(at("100.101.102.103")))
    assertEquals(client("100.101.102.103, ::ffff:127.0.0.1"), Right(at("100.101.102.103")), "a mapped proxy is one")
    val twoProxies = policyWith("127.0.0.0/8", "192.0.2.2/32")
    val noProxy = policyWith()
    assertEquals(client("203.0.113.9, 192.0.2.1, 192.0.2.2", twoProxies), Right(at("192.0.2.1")))
    assertEquals(client("203.0.113.9, 192.0.2.1, 192.0.2.2", policyWith("192.0.2.1/32", "192.0.2.2/32")), Right(at("203.0.113.9")))
    assertEquals(client("203.0.113.9, 192.0.2.1", noProxy), Right(at("192.0.2.1")))

  test("the walk: a hidden hop reached before the client fails; one beyond it is never read"):
    def client(chain: String) =
      hops(ForwardedChain.ofForwarded(texts(chain))).flatMap(h => ForwardedChain.from(h)).flatMap(_.client(loopbackProxy))
    assertEquals(client("for=unknown"), Left(Fault.Hidden))
    assertEquals(client("for=100.101.102.103, for=_hidden"), Left(Fault.Hidden))
    assertEquals(client("for=_hidden, for=127.0.0.1"), Left(Fault.Hidden))
    assertEquals(client("for=_hidden, for=100.101.102.103, for=127.0.0.1"), Right(at("100.101.102.103")))

  test("a chain holds one hop at least and the cap at most"):
    assertEquals(ForwardedChain.from(Vector.empty), Left(Fault.Malformed))
    assertEquals(ForwardedChain.from(Vector.fill(ForwardedChain.MaxHops + 1)(Hop.Hidden)), Left(Fault.TooLong))
    assert(ForwardedChain.from(Vector(Hop.Hidden)).isRight)
