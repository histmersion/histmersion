package com.histmersion
package api.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** An agent run's vocabularies as the wire spells them (the api's own copies of the model's). */
object Run:

  /** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a
    * person's pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval
    * it waits on; `Cancelled` and the other terminal states are final.
    */
  enum State(val wire: State.Wire) derives CanEqual:
    case Queued extends State(State.Wire("queued"))
    case Running extends State(State.Wire("running"))
    case Paused extends State(State.Wire("paused"))
    case WaitingApproval extends State(State.Wire("waiting"))
    case Done extends State(State.Wire("done"))
    case Failed extends State(State.Wire("failed"))
    case Cancelled extends State(State.Wire("cancelled"))

    def isTerminal: Boolean = (this == Done || this == Failed || this == Cancelled)

  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: State, to: State): Option[State] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                     => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
      case _                                                                              => None
