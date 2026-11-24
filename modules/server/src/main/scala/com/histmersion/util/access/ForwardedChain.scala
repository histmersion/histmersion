package com.histmersion
package util.access

import java.net.InetAddress
import java.util.Locale

import scala.annotation.tailrec
import scala.util.matching.Regex

import com.histmersion.util.access.ClientAddress.Fault

final case class ForwardedChain private (hops: Vector[ForwardedChain.Hop]):
  import ForwardedChain.Hop

  def client(policy: AccessPolicy): Either[Fault, InetAddress] =
    @tailrec def walk(at: Int): Either[Fault, InetAddress] =
      hops(at) match
        case Hop.Hidden                                                    => Left(Fault.Hidden)
        case Hop.Address(address) if at > 0 && policy.proxiedFrom(address) => walk(at - 1)
        case Hop.Address(address)                                          => Right(address)
    walk(hops.size - 1)

object ForwardedChain:
  enum Hop derives CanEqual:
    case Address(address: InetAddress)
    case Hidden

  val MaxHops = 16

  def from(hops: Vector[Hop]): Either[Fault, ForwardedChain] =
    if hops.isEmpty then Left(Fault.Malformed)
    else if hops.size > MaxHops then Left(Fault.TooLong)
    else Right(ForwardedChain(hops))

  def sources(marked: Forwarding.Marked): List[Either[Fault, ForwardedChain]] =
    List(ofForwarded(marked.forwarded), ofForwardedFor(marked.forwardedFor), ofRealIp(marked.realIp)).flatten

  def ofForwarded(values: List[Admission.HeaderText]): Option[Either[Fault, ForwardedChain]] =
    if values.isEmpty then None
    else
      split(joined(values), ',').flatMap(elements => traverse(elements)(forOf)) match
        case Left(fault)                           => Some(Left(fault))
        case Right(fors) if fors.forall(_.isEmpty) => None
        case Right(fors) => Some(traverse(fors)(_.fold[Either[Fault, Hop]](Right(Hop.Hidden))(node)).flatMap(chain))

  def ofForwardedFor(values: List[Admission.HeaderText]): Option[Either[Fault, ForwardedChain]] =
    Option.when(values.nonEmpty)(split(joined(values), ',').flatMap(entries => traverse(entries)(node)).flatMap(chain))

  def ofRealIp(values: List[Admission.HeaderText]): Option[Either[Fault, ForwardedChain]] =
    values match
      case Nil        => None
      case one :: Nil =>
        Some(split(Admission.HeaderText.joined(List(one)), ',').flatMap {
          case single :: Nil => node(single).flatMap(hop => from(Vector(hop)))
          case _             => Left(Fault.Malformed)
        })
      case _ => Some(Left(Fault.Malformed))

  def node(raw: String): Either[Fault, Hop] =
    unquoted(raw.trim).flatMap { text =>
      if text.equalsIgnoreCase("unknown") then Right(Hop.Hidden)
      else if text.startsWith("_") then Either.cond(Obfuscated.matches(text), Hop.Hidden, Fault.Malformed)
      else address(text).map(Hop.Address(_)).toRight(Fault.Malformed)
    }

  private val Token: Regex = """[!#$%&'*+.^_`|~0-9A-Za-z-]+""".r
  private val Obfuscated: Regex = """_[A-Za-z0-9._-]+""".r
  private val Digits: Regex = """\d{1,5}""".r
  private val QuotedContent: Regex = """(?:[^"\\]|\\.)*""".r
  private val Escaped: Regex = """\\(.)""".r
  private val PortMax = 65535

  private enum Quoting derives CanEqual:
    case Outside, Inside

  private def chain(hops: List[Hop]): Either[Fault, ForwardedChain] = from(hops.toVector)

  private def joined(values: List[Admission.HeaderText]): String = Admission.HeaderText.joined(values)

  private def forOf(element: String): Either[Fault, Option[String]] =
    split(element, ';').flatMap { parts =>
      val pairs = parts.map(_.trim).filter(_.nonEmpty)
      if pairs.isEmpty then Left(Fault.Malformed)
      else
        traverse(pairs)(pair).flatMap { named =>
          named.collect { case ("for", value) => value } match
            case Nil          => Right(None)
            case value :: Nil => Right(Some(value))
            case _            => Left(Fault.Malformed)
        }
    }

  private def pair(text: String): Either[Fault, (String, String)] =
    val at = text.indexOf('=')
    val name = if at > 0 then text.take(at).trim else ""
    Either.cond(Token.matches(name), name.toLowerCase(Locale.ROOT) -> text.drop(at + 1).trim, Fault.Malformed)

  private def unquoted(text: String): Either[Fault, String] =
    if !text.startsWith("\"") then Either.cond(!text.exists(c => c == '"' || c == '\\'), text, Fault.Malformed)
    else
      val inner = text.drop(1).dropRight(1)
      Either.cond(
        text.length >= 2 && text.endsWith("\"") && QuotedContent.matches(inner),
        Escaped.replaceAllIn(inner, m => Regex.quoteReplacement(m.group(1))).trim,
        Fault.Malformed
      )

  private def address(text: String): Option[InetAddress] =
    if text.startsWith("[") then
      text.indexOf(']') match
        case -1    => None
        case close =>
          val inside = text.substring(1, close)
          val rest = text.substring(close + 1)
          Cidr.literal(inside).filter(_ => inside.contains(':') && (rest.isEmpty || portSuffix(rest)))
    else
      text.count(_ == ':') match
        case 0 => Cidr.literal(text)
        case 1 =>
          val at = text.indexOf(':')
          Cidr.literal(text.take(at)).filter(_ => portSuffix(text.drop(at)))
        case _ => Cidr.literal(text)

  private def portSuffix(text: String): Boolean =
    text.startsWith(":") && {
      val port = text.drop(1)
      Obfuscated.matches(port) || (Digits.matches(port) && port.toInt <= PortMax)
    }

  private def split(text: String, separator: Char): Either[Fault, List[String]] =
    @tailrec def loop(at: Int, start: Int, quoting: Quoting, parts: List[String]): Either[Fault, List[String]] =
      if at >= text.length then
        if quoting == Quoting.Inside then Left(Fault.Malformed) else Right((text.substring(start) :: parts).reverse)
      else
        (text.charAt(at), quoting) match
          case ('"', Quoting.Outside)                 => loop(at + 1, start, Quoting.Inside, parts)
          case ('"', Quoting.Inside)                  => loop(at + 1, start, Quoting.Outside, parts)
          case ('\\', Quoting.Inside)                 => loop(at + 2, start, quoting, parts)
          case (c, Quoting.Outside) if c == separator =>
            loop(at + 1, at + 1, quoting, text.substring(start, at) :: parts)
          case _ => loop(at + 1, start, quoting, parts)
    loop(0, 0, Quoting.Outside, Nil)

  private def traverse[A, B](items: List[A])(each: A => Either[Fault, B]): Either[Fault, List[B]] =
    @tailrec def loop(rest: List[A], done: List[B]): Either[Fault, List[B]] =
      rest match
        case Nil          => Right(done.reverse)
        case head :: tail =>
          each(head) match
            case Left(fault)  => Left(fault)
            case Right(value) => loop(tail, value :: done)
    loop(items, Nil)
