package com.histmersion.api.automation

/** An agent run's vocabularies as the wire spells them (the api's own copies of the model's). */
object Run:

  /** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a
    * person's pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval
    * it waits on; `Cancelled` and the other terminal states are final.
    */
  enum State(val wire: String):
    case Queued extends State("queued")
    case Running extends State("running")
    case Paused extends State("paused")
    case WaitingApproval extends State("waiting")
    case Done extends State("done")
    case Failed extends State("failed")
    case Cancelled extends State("cancelled")

    def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s.trim.toLowerCase)

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: State, to: State): Option[State] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                     => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
      case _                                                                              => None
