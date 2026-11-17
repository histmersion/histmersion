package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Who the server lets in. A request from a network in `trustedNetworks` acts as `trustedAs` with no credential; one
  * from a network in `authenticatedNetworks` needs `Authorization: Bearer <apiKey>`, reads and writes alike; one from
  * any other network is refused. The longest prefix that holds the peer decides. `hosts` are the names, besides the
  * loopback ones, the bind address and the public URL's host, a request may give in its `Host` header; `origins` the
  * pages, besides this server's own, that may send changes. The server does not start when its bind address can be
  * reached from a network these settings leave open.
  */
final case class AuthConfig(
    apiKey: AuthConfig.ApiKey,
    trustedNetworks: List[AuthConfig.Network],
    authenticatedNetworks: List[AuthConfig.Network],
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

  /** The principal a trusted network's requests act as (`owner`). */
  type TrustedAs = TrustedAs.T
  object TrustedAs extends Newtype[String, Pure]

  /** A name a request may give in its `Host` header (`histmersion.example.edu`, `mymac.tail1234.ts.net`). */
  type HostName = HostName.T
  object HostName extends Newtype[String, Pure]

  /** A page origin that may send changes (`https://histmersion.example.edu`). */
  type Origin = Origin.T
  object Origin extends Newtype[String, Pure]
