package com.histmersion
package server

import sttp.tapir.server.netty.NettyConfig
import scala.concurrent.duration.*

class ServerTimeoutsSuite extends munit.FunSuite:

  test("a connection waiting on a long answer is not closed as idle before the request timeout"):
    val nc = Composition.timeouts(NettyConfig.default, 1800.seconds)
    assertEquals(nc.requestTimeout, Some(1800.seconds))
    assertEquals(nc.idleTimeout, Some(1800.seconds))
    assert(NettyConfig.default.idleTimeout.exists(_ < 1800.seconds), "tapir's idle default is no longer shorter")
