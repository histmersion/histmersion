package com.histmersion
package util.access

import java.net.{Inet4Address, InetAddress}

import scala.util.Try

/** A network: the addresses of one family whose first `prefix` bits are those of `first`, its lowest address. Parsed
  * from CIDR text (`127.0.0.0/8`, `::1/128`) or a bare address (a network of one); host bits set in the text are
  * cleared (`127.0.0.1/8` is `127.0.0.0/8`). Only literal addresses are read: no text is ever looked up in DNS.
  */
final case class Cidr private (family: Cidr.Family, first: BigInt, prefix: Int):
  import Cidr.{numbered, render}

  /** The highest address of the network. */
  def last: BigInt = first + (BigInt(1) << (family.bits - prefix)) - 1

  /** Whether `address` lies in the network. An IPv4-mapped IPv6 address (`::ffff:a.b.c.d`) is its IPv4 address. */
  def holds(address: InetAddress): Boolean =
    val (f, n) = numbered(address)
    f == family && n >= first && n <= last

  override def toString: String = s"${render(family, first)}/$prefix"

object Cidr:
  /** An address family and the width of its addresses. */
  enum Family(val bits: Int) derives CanEqual:
    case V4 extends Family(32)
    case V6 extends Family(128)

  /** Every IPv4 address, and every IPv6 one. */
  val everything: List[Cidr] = List(Cidr(Family.V4, BigInt(0), 0), Cidr(Family.V6, BigInt(0), 0))

  /** The loopback networks: `127.0.0.0/8` and `::1/128`. */
  val loopback: List[Cidr] = List(Cidr(Family.V4, BigInt(127) << 24, 8), Cidr(Family.V6, BigInt(1), 128))

  private val Dotted = """(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""".r
  private val Colons = """[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*""".r

  /** The network `text` names, or `None` when it is no literal address with an optional `/prefix` in range. */
  def parse(text: String): Option[Cidr] =
    val trimmed = text.trim.stripPrefix("[").replace("]", "")
    val (address, bits) = trimmed.split('/') match
      case Array(a)    => (a, None)
      case Array(a, p) => (a, Some(p))
      case _           => ("", None)
    for
      found <- literal(address)
      (family, number) = numbered(found)
      prefix <- bits.fold(Option(family.bits))(p => p.toIntOption.filter(n => n >= 0 && n <= family.bits))
    yield
      val host = (BigInt(1) << (family.bits - prefix)) - 1
      Cidr(family, number &~ host, prefix)

  /** A literal IPv4 (four decimal octets) or IPv6 address; anything else, a name above all, is none. */
  def literal(text: String): Option[InetAddress] = text match
    case Dotted(a, b, c, d) =>
      val octets = List(a, b, c, d).map(_.toInt)
      Option.when(octets.forall(_ <= 255))(InetAddress.getByAddress(octets.map(_.toByte).toArray))
    // a text with a colon is only ever parsed as an IPv6 literal, never looked up; `::ffff:a.b.c.d` comes back as the
    // IPv4 address it maps
    case Colons() if !text.contains('%') => Try(InetAddress.getByName(text)).toOption
    case _                               => None

  /** The family of `address` and its number. */
  def numbered(address: InetAddress): (Family, BigInt) =
    val family = if address.isInstanceOf[Inet4Address] then Family.V4 else Family.V6
    (family, BigInt(1, address.getAddress))

  /** An address of `family` written as the JDK writes it. */
  private def render(family: Family, number: BigInt): String =
    val bytes = number.toByteArray.reverse.padTo(family.bits / 8, 0.toByte).take(family.bits / 8).reverse
    val text = InetAddress.getByAddress(bytes).getHostAddress
    if family == Family.V6 then compressed(text) else text

  /** `0:0:0:0:0:0:0:1` as `::1`: the longest run of zero groups folded once. */
  private def compressed(full: String): String =
    val groups = full.split(':').toVector.map(g => g.dropWhile(_ == '0').padTo(1, '0'))
    def zerosFrom(i: Int): Int = groups.drop(i).takeWhile(_ == "0").size
    @scala.annotation.tailrec
    def longest(i: Int, start: Int, length: Int): (Int, Int) =
      if i >= groups.size then (start, length)
      else
        val here = zerosFrom(i)
        if here > length then longest(i + here.max(1), i, here) else longest(i + here.max(1), start, length)
    longest(0, 0, 0) match
      case (start, length) if length > 1 =>
        s"${groups.take(start).mkString(":")}::${groups.drop(start + length).mkString(":")}"
      case _ => groups.mkString(":")
