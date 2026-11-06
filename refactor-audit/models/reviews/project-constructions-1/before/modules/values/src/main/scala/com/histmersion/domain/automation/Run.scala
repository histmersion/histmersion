package com.histmersion.domain.automation

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Run:

  /** An agent run's id. A run is a background job, so this is that job's id; its own type says which jobs are runs. */
  type Id = Id.T

  object Id extends RefinedType[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]

  /** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a
    * person's pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval
    * it waits on; `Cancelled` and the other terminal states are final.
    */
  enum State(val wire: State.Wire):
    case Queued extends State("queued")
    case Running extends State("running")
    case Paused extends State("paused")
    case WaitingApproval extends State("waiting")
    case Done extends State("done")
    case Failed extends State("failed")
    case Cancelled extends State("cancelled")

    def isTerminal: com.histmersion.domain.automation.Run.State.IsTerminalResult = this == Done || this == Failed || this == Cancelled

  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type IsTerminalResult = IsTerminalResult.T
    object IsTerminalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.automation.Run.State.ParseS): Option[State] = values.find(_.wire == s.trim.toLowerCase)

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: State, to: State): Option[State] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                     => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
      case _                                                                              => None
