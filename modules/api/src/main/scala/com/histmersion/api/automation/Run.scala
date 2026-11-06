package com.histmersion.api.automation

/** An agent run's vocabularies as the wire spells them (the api's own copies of the model's). */
object Run:

  /** An agent run's state, stored in `jobs.status`. `Paused` is resumable (a budget breach, a server restart, a
    * person's pause); `WaitingApproval` holds no worker and goes back to the queue when a person decides the approval
    * it waits on; `Cancelled` and the other terminal states are final.
    */
  enum State(val wire: State.Wire):
    case Queued extends State(_root_.com.histmersion.api.automation.Run.State.Wire("queued"))
    case Running extends State(_root_.com.histmersion.api.automation.Run.State.Wire("running"))
    case Paused extends State(_root_.com.histmersion.api.automation.Run.State.Wire("paused"))
    case WaitingApproval extends State(_root_.com.histmersion.api.automation.Run.State.Wire("waiting"))
    case Done extends State(_root_.com.histmersion.api.automation.Run.State.Wire("done"))
    case Failed extends State(_root_.com.histmersion.api.automation.Run.State.Wire("failed"))
    case Cancelled extends State(_root_.com.histmersion.api.automation.Run.State.Wire("cancelled"))

    def isTerminal: com.histmersion.api.automation.Run.State.IsTerminalResult = _root_.com.histmersion.api.automation.Run.State.IsTerminalResult(this == Done || this == Failed || this == Cancelled)

  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type IsTerminalResult = IsTerminalResult.T
    object IsTerminalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.Run.State.ParseS): Option[State] = values.find(_.wire == _root_.com.histmersion.api.automation.Run.State.Wire((s).value.trim.toLowerCase))

    /** The legal transitions. `None` = refuse (the run has moved on). Resume is `Paused → Queued`. */
    def next(from: State, to: State): Option[State] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                     => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
      case _                                                                              => None
