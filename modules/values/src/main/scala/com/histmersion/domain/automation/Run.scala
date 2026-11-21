package com.histmersion
package domain.automation

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.any.Pure

object Run:

  type Id = Id.T

  object Id extends Newtype[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]

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

    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

    def next(from: State, to: State): Option[State] = (from, to) match
      case (Queued, Running) | (Queued, Cancelled) | (Queued, Paused)                     => Some(to)
      case (Running, Done) | (Running, Failed) | (Running, Paused) | (Running, Cancelled) => Some(to)
      case (Running, WaitingApproval)                                                     => Some(to)
      case (Paused, Queued) | (Paused, Cancelled)                                         => Some(to)
      case (WaitingApproval, Queued) | (WaitingApproval, Cancelled)                       => Some(to)
      case _                                                                              => None
