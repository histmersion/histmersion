package com.histmersion
package util.access

import java.net.InetAddress

/** CIDR text as the `auth.*-networks` settings give it: parsed, normalised, and holding the addresses it should. No
  * network: every address is a literal.
  */
class CidrSuite extends munit.FunSuite:

  private def at(text: String): InetAddress = Cidr.literal(text).getOrElse(fail(s"no literal: $text"))
  private def cidr(text: String): Cidr = Cidr.parse(text).getOrElse(fail(s"no network: $text"))

  test("IPv4 and IPv6 networks, a bare address as a network of one, host bits cleared"):
    assertEquals(cidr("127.0.0.0/8").toString, "127.0.0.0/8")
    assertEquals(cidr("127.0.0.1/8").toString, "127.0.0.0/8")
    assertEquals(cidr("100.64.0.0/10").toString, "100.64.0.0/10")
    assertEquals(cidr("10.1.2.3").toString, "10.1.2.3/32")
    assertEquals(cidr("::1").toString, "::1/128")
    assertEquals(cidr("[::1]/128").toString, "::1/128")
    assertEquals(cidr("fd7a:115c:a1e0::/48").toString, "fd7a:115c:a1e0::/48")
    assertEquals(cidr("::/0").toString, "::/0")
    assertEquals(cidr("0.0.0.0/0").toString, "0.0.0.0/0")

  test("anything but a literal address with a prefix in range is no network — and no name is ever looked up"):
    List(
      "localhost",
      "example.com",
      "127.0.0.0/33",
      "::/129",
      "127.0.0/8",
      "256.1.1.1",
      "1.2.3.4/-1",
      "1.2.3.4/8/8",
      "",
      "fe80::1%en0",
      "1.2.3.4/x"
    ).foreach(t => assertEquals(Cidr.parse(t), None, t))

  test("a network holds exactly its addresses, of its own family"):
    val loop = cidr("127.0.0.0/8")
    assert(loop.holds(at("127.0.0.1")))
    assert(loop.holds(at("127.255.255.255")))
    assert(!loop.holds(at("128.0.0.0")))
    assert(!loop.holds(at("::1")))
    val tailnet = cidr("100.64.0.0/10")
    assert(tailnet.holds(at("100.64.0.1")) && tailnet.holds(at("100.127.255.254")))
    assert(!tailnet.holds(at("100.128.0.1")) && !tailnet.holds(at("100.63.255.255")))
    assert(cidr("::1/128").holds(at("::1")) && !cidr("::1/128").holds(at("::2")))
    assert(cidr("0.0.0.0/0").holds(at("203.0.113.9")))

  test("an IPv4-mapped IPv6 peer is its IPv4 address"):
    assert(cidr("127.0.0.0/8").holds(at("::ffff:127.0.0.1")))
    assert(!cidr("::/0").holds(at("::ffff:127.0.0.1")))

  test("the first and last address of a network"):
    val n = cidr("192.168.1.0/24")
    assertEquals(n.last - n.first, BigInt(255))
    assertEquals(cidr("::/0").last, (BigInt(1) << 128) - 1)
