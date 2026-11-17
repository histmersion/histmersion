package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AuthConfig

/** The `auth` settings of a test that stands for a client on another network: the API key `key`, and loopback listed as
  * an AUTHENTICATED network, not a trusted one, so the test's own requests (all from 127.0.0.1) need the key — reads
  * and writes alike — as a remote client's would.
  */
object KeyedLoopback:
  def apply(auth: AuthConfig, key: String): AuthConfig =
    auth
      .modify(_.apiKey)
      .setTo(AuthConfig.ApiKey(key))
      .modify(_.trustedNetworks)
      .setTo(Nil)
      .modify(_.authenticatedNetworks)
      .setTo(List(AuthConfig.Network("127.0.0.0/8"), AuthConfig.Network("::1/128")))
