package com.histmersion.server

import com.histmersion.config.{Environment, UserPaths}

import java.time.Clock

/** What the process reads from its surroundings, read once by the entry point and passed down: the clock, the
  * environment and the per-user paths that follow from it.
  */
final case class SystemContext(clock: Clock, environment: Environment, userPaths: UserPaths, timer: com.histmersion.util.time.Timer = new com.histmersion.util.time.SystemTimer, pause: com.histmersion.util.time.Pause = new com.histmersion.util.time.ThreadPause)

object SystemContext:
  /** A server started by a test: the system clock, and no environment, so nothing of the machine running it leaks in.
    */
  def jvmHome(): com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of(System.getProperty("user.home")))
  val isolated: SystemContext = SystemContext(Clock.systemUTC(), Environment.empty, UserPaths(Environment.empty, ""))
