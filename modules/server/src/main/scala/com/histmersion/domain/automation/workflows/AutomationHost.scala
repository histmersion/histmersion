package com.histmersion.domain.automation.workflows

import com.histmersion.api.{ApiError, Goal}

/** What the engine asks of the rest of the server while the domains still keep it in the composition's handlers: the
  * composition root implements it over them (`server/agents/TransitionHost`), and each operation moves to the owning
  * service as its slice comes.
  */
trait AutomationHost:
  /** A goal by its slug, a renamed goal's old slug answering as the goal it was renamed to. */
  def goal(slug: String): Either[ApiError, Goal]
