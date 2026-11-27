package com.histmersion
package server

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AuthConfig
import com.histmersion.lint.{tolerated, LintRule}

/** The `auth` settings of a test that stands for a client on another network: the API key `key`, and loopback listed as
  * an AUTHENTICATED network, not a trusted one, so the test's own requests (all from 127.0.0.1) need the key — reads
  * and writes alike — as a remote client's would.
  */
object KeyedLoopback:
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def apply(auth: AuthConfig, key: String): AuthConfig =
    auth
      .modify(_.apiKey)
      .setTo(AuthConfig.ApiKey(key))
      .modify(_.trustedNetworks)
      .setTo(Nil)
      .modify(_.authenticatedNetworks)
      .setTo(List(AuthConfig.Network("127.0.0.0/8"), AuthConfig.Network("::1/128")))
