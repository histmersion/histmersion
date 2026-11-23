package com.histmersion
package util.access

import java.net.InetAddress

enum ClientAddress derives CanEqual:
  case Direct(peer: InetAddress)
  case Forwarded(client: InetAddress)
  case Unusable(fault: ClientAddress.Fault)
  case Undeclared

object ClientAddress:
  enum Fault derives CanEqual:
    case Absent, Malformed, Hidden, TooLong, Conflicting

  def of(peer: InetAddress, forwarding: Forwarding, policy: AccessPolicy): ClientAddress =
    forwarding match
      case Forwarding.Absent         => Direct(peer)
      case marked: Forwarding.Marked =>
        if policy.proxiedFrom(peer) then reported(marked, policy) else Undeclared

  private def reported(marked: Forwarding.Marked, policy: AccessPolicy): ClientAddress =
    val clients = ForwardedChain.sources(marked).map(_.flatMap(_.client(policy)))
    clients.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Unusable(fault)
      case None        =>
        clients.collect { case Right(address) => address } match
          case Nil                                                           => Unusable(Fault.Absent)
          case first :: others if others.forall(agrees(_, first, policy)) => Forwarded(first)
          case _                                                             => Unusable(Fault.Conflicting)

  private def agrees(other: InetAddress, client: InetAddress, policy: AccessPolicy): Boolean =
    Cidr.sameAddress(other, client) || policy.proxiedFrom(other)
