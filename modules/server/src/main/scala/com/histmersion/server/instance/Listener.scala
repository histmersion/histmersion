package com.histmersion
package server.instance

import ox.Ox
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerBinding}

import com.histmersion.config.ServerConfig
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.lint.{LintRule, tolerated}

/** A configured server and every address it listens on (`server.host`): one netty listener per address, all serving the
  * same endpoints, each behind the same access gate, all on the one configured port (or each on an ephemeral port of
  * its own when the port is 0).
  */
final case class Listener(server: NettySyncServer, binds: NonEmptyVector[ServerConfig.Host]):

  /** Binds every address, in order: the bindings, each stopped on its own (or with the enclosing scope). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def start()(using Ox): NonEmptyVector[NettySyncServerBinding] = binds.map(b => server.host(s"$b").start())
