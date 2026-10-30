package com.histmersion.values

/** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a person's
  * pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval it waits on;
  * `Cancelled` and the other terminal states are final.
  */
enum RunState(val wire: String):
  case Queued extends RunState("queued")
  case Running extends RunState("running")
  case Paused extends RunState("paused")
  case WaitingApproval extends RunState("waiting")
  case Done extends RunState("done")
  case Failed extends RunState("failed")
  case Cancelled extends RunState("cancelled")

  def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

object RunState:
  def parse(s: String): Option[RunState] = values.find(_.wire == s.trim.toLowerCase)

  /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
  def next(from: RunState, to: RunState): Option[RunState] = (from, to) match
    case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
    case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
    case (Running, WaitingApproval)                                                     => Some(to)
    case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
    case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
    case _                                                                              => None
