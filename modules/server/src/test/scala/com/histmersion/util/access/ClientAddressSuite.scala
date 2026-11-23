package com.histmersion
package util.access

import java.net.{Inet6Address, InetAddress}

import scala.jdk.CollectionConverters.*

import org.ekrich.config.ConfigFactory

import com.histmersion.config.AppConfig
import com.histmersion.util.access.ClientAddress.Fault

class ClientAddressSuite extends munit.FunSuite:

  private def policyWith(proxyNetworks: String*): AccessPolicy =
    val listed = proxyNetworks.map(n => s"\"$n\"").mkString("[", ", ", "]")
    val cfg = AppConfig
      .loadWithUserText(ConfigFactory.empty, s"histmersion.auth.proxy-networks = $listed")
      .fold(e => fail(s"$e"), _.config)
    AccessPolicy.from(cfg.auth, cfg.server).fold(e => fail(s"$e"), identity)

  private val loopbackProxy = policyWith("127.0.0.0/8", "::1/128")
  private val noProxy = policyWith()

  private def at(text: String): InetAddress = Cidr.literal(text).getOrElse(fail(s"no literal: $text"))
  private def texts(values: String*): List[Admission.HeaderText] = Admission.HeaderText.all(values.asJava)

  private val loopback = at("127.0.0.1")
  private val tailnet = at("100.101.102.103")

  private def marked(
      forwarded: List[String] = Nil,
      forwardedFor: List[String] = Nil,
      realIp: List[String] = Nil
  ): Forwarding = Forwarding.Marked(texts(forwarded*), texts(forwardedFor*), texts(realIp*))

  private def of(forwarding: Forwarding, peer: InetAddress = loopback, policy: AccessPolicy = loopbackProxy) =
    ClientAddress.of(peer, forwarding, policy)

  test("no forwarding header: the peer itself, whatever the proxy networks"):
    for policy <- List(noProxy, loopbackProxy) do
      assertEquals(of(Forwarding.Absent, policy = policy), ClientAddress.Direct(loopback))
      assertEquals(of(Forwarding.Absent, tailnet, policy), ClientAddress.Direct(tailnet))

  test("a forwarding header from a peer in no proxy network is undeclared, whatever it reports"):
    assertEquals(of(marked(forwardedFor = List("100.101.102.103")), policy = noProxy), ClientAddress.Undeclared)
    assertEquals(of(marked(forwardedFor = List("100.101.102.103")), tailnet), ClientAddress.Undeclared)
    assertEquals(of(marked(forwarded = List("for=garbage")), tailnet), ClientAddress.Undeclared)
    assertEquals(of(Forwarding.of(name => if name == "Via" then texts("1.1 p") else Nil), tailnet), ClientAddress.Undeclared)

  test("from a declared proxy: the client its chain reports"):
    assertEquals(of(marked(forwardedFor = List("100.101.102.103"))), ClientAddress.Forwarded(tailnet))
    assertEquals(of(marked(forwarded = List("for=\"[2001:db8::1]:4711\""))), ClientAddress.Forwarded(at("2001:db8::1")))
    assertEquals(of(marked(realIp = List("100.101.102.103"))), ClientAddress.Forwarded(tailnet))
    assertEquals(of(marked(forwardedFor = List("203.0.113.9, 100.101.102.103"))), ClientAddress.Forwarded(tailnet))

  test("chains: with only the nearest proxy declared, the hop before it is the client; with both, the original"):
    val chain = marked(forwardedFor = List("203.0.113.9, 192.0.2.1"))
    assertEquals(of(chain, policy = loopbackProxy), ClientAddress.Forwarded(at("192.0.2.1")))
    assertEquals(of(chain, policy = policyWith("127.0.0.0/8", "192.0.2.1/32")), ClientAddress.Forwarded(at("203.0.113.9")))

  test("an IPv4-mapped peer is the declared proxy it maps, and a mapped report is its IPv4 address"):
    val mappedPeer = Inet6Address.getByAddress(null, Array.fill[Byte](10)(0) ++ Array[Byte](-1, -1, 127, 0, 0, 1), -1)
    assertEquals(of(marked(forwardedFor = List("100.101.102.103")), mappedPeer), ClientAddress.Forwarded(tailnet))
    assertEquals(of(marked(forwardedFor = List("::ffff:100.101.102.103"))), ClientAddress.Forwarded(tailnet))
    assertEquals(of(marked(forwardedFor = List("::ffff:127.0.0.1"))), ClientAddress.Forwarded(loopback))
    assertEquals(
      of(marked(forwardedFor = List("100.101.102.103"), realIp = List("::ffff:100.101.102.103"))),
      ClientAddress.Forwarded(tailnet),
      "the mapped and the plain spelling agree"
    )

  test("Forwarded takes precedence; the other sources must agree with it or name a declared proxy"):
    assertEquals(
      of(marked(forwarded = List("for=100.101.102.103"), forwardedFor = List("100.101.102.103"))),
      ClientAddress.Forwarded(tailnet)
    )
    assertEquals(
      of(marked(forwarded = List("for=100.101.102.103"), forwardedFor = List("127.0.0.1"))),
      ClientAddress.Forwarded(tailnet)
    )
    assertEquals(
      of(marked(forwardedFor = List("203.0.113.9, 192.0.2.1"), realIp = List("127.0.0.1"))),
      ClientAddress.Forwarded(at("192.0.2.1")),
      "nginx's X-Real-IP of a proxy in front of it says nothing of the client"
    )

  test("sources that name two clients conflict: a header a proxy passed on untouched cannot pick the client"):
    assertEquals(
      of(marked(forwarded = List("for=100.101.102.103"), forwardedFor = List("203.0.113.9"))),
      ClientAddress.Unusable(Fault.Conflicting),
      "a client's own Forwarded beside the X-Forwarded-For its proxy wrote"
    )
    assertEquals(
      of(marked(forwardedFor = List("100.101.102.103"), realIp = List("203.0.113.9"))),
      ClientAddress.Unusable(Fault.Conflicting),
      "a client's own X-Forwarded-For beside the X-Real-IP its proxy wrote"
    )
    assertEquals(
      of(marked(forwarded = List("for=127.0.0.1"), forwardedFor = List("203.0.113.9"))),
      ClientAddress.Unusable(Fault.Conflicting),
      "a forged chain of proxies only"
    )

  test("from a declared proxy, no usable address fails closed: unusable, never the peer"):
    assertEquals(of(marked()), ClientAddress.Unusable(Fault.Absent), "only Via, X-Forwarded-Proto or -Host")
    assertEquals(of(marked(forwarded = List("proto=https"))), ClientAddress.Unusable(Fault.Absent))
    assertEquals(of(marked(forwarded = List("for=unknown"))), ClientAddress.Unusable(Fault.Hidden))
    assertEquals(of(marked(forwarded = List("for=_hidden"))), ClientAddress.Unusable(Fault.Hidden))
    assertEquals(of(marked(forwardedFor = List(""))), ClientAddress.Unusable(Fault.Malformed))
    assertEquals(of(marked(forwardedFor = List("garbage"))), ClientAddress.Unusable(Fault.Malformed))
    assertEquals(
      of(marked(forwardedFor = List("100.101.102.103"), realIp = List("garbage"))),
      ClientAddress.Unusable(Fault.Malformed),
      "every source must parse"
    )
    assertEquals(
      of(marked(forwardedFor = List(List.fill(ForwardedChain.MaxHops + 1)("100.101.102.103").mkString(",")))),
      ClientAddress.Unusable(Fault.TooLong)
    )
