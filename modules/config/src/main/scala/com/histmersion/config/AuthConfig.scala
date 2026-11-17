package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Who the server lets in. A request from a network in `trustedNetworks` acts as `trustedAs` with no credential; one
  * from a network in `authenticatedNetworks` needs `Authorization: Bearer <apiKey>`, reads and writes alike; one from a
  * network in `refusedNetworks` is turned away, and so is one from a network no list names. The longest prefix that
  * holds the peer decides. `proxy` declares a reverse proxy on this machine (`local`): the requests it forwards reach
  * the server from loopback, and carry `Forwarded` or `X-Forwarded-For`, so they are held to the authenticated rules
  * however loopback is listed. `hosts` are the names, besides the loopback ones, the bind addresses and the public
  * URL's host, a request may give in its `Host` header; `origins` the pages, besides this server's own, that may send
  * changes. The server does not start when one of its bind addresses can be reached from a network these settings leave
  * open, or when its public URL is HTTPS while loopback is trusted and no proxy is declared.
  */
final case class AuthConfig(
    apiKey: AuthConfig.ApiKey,
    trustedNetworks: List[AuthConfig.Network],
    authenticatedNetworks: List[AuthConfig.Network],
    refusedNetworks: List[AuthConfig.Network],
    proxy: AuthConfig.Proxy,
    trustedAs: AuthConfig.TrustedAs,
    hosts: List[AuthConfig.HostName],
    origins: List[AuthConfig.Origin]
) derives ConfigReader

object AuthConfig:
  type ApiKey = ApiKey.T
  object ApiKey extends Newtype[String, Pure]

  /** A network as CIDR text (`127.0.0.0/8`, `::1/128`, `100.64.0.0/10`); a bare address is a network of one. */
  type Network = Network.T
  object Network extends Newtype[String, Pure]

  /** The reverse proxy in front of the server, as written: `none` or `local` (one on this machine). */
  type Proxy = Proxy.T
  object Proxy extends Newtype[String, Pure]

  /** The principal a trusted network's requests act as (`owner`). */
  type TrustedAs = TrustedAs.T
  object TrustedAs extends Newtype[String, Pure]

  /** A name a request may give in its `Host` header (`histmersion.example.edu`, `mymac.tail1234.ts.net`). */
  type HostName = HostName.T
  object HostName extends Newtype[String, Pure]

  /** A page origin that may send changes (`https://histmersion.example.edu`). */
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]
