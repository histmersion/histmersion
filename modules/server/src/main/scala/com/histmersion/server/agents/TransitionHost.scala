package com.histmersion.server.agents

import com.histmersion.api.{ApiError, Goal}
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.server.Handlers

/** The engine's [[AutomationHost]] over today's handlers, until each operation's owner offers its own service. */
final class TransitionHost(h: Handlers) extends AutomationHost:
  def goal(slug: String): Either[ApiError, Goal] = h.getGoal(slug)
