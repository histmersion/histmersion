package com.histmersion.api

/** The lifecycle of a job. `queued → running → done|failed`; a long background job a person paused is `paused`
  * (`running` again when resumed) and one they stopped `cancelled`. A restart turns every `running` row into
  * `failed: interrupted`, because nothing survives the process.
  */
enum JobState:
  case Queued, Running, Done, Failed, Paused, Cancelled

  def wire: String = toString.toLowerCase
  def isTerminal: Boolean = this == Done || this == Failed || this == Cancelled

object JobState:
  val Interrupted = "interrupted"

  def parse(s: String): Option[JobState] =
    values.find(_.wire == s.trim.toLowerCase)

  /** The legal transitions, as a total function: `None` means "refuse, the job has moved on". */
  def next(from: JobState, to: JobState): Option[JobState] = (from, to) match
    case (Queued, Running) => Some(Running)
    case (Queued, Failed)  => Some(Failed) // cancelled before a worker picked it up
    case (Running, Done)   => Some(Done)
    case (Running, Failed) => Some(Failed)
    // a person's pause or cancel of a long background job (an agent run's own states are RunState's)
    case (Running, Paused)    => Some(Paused)
    case (Paused, Running)    => Some(Running)
    case (Running, Cancelled) => Some(Cancelled)
    case (Paused, Cancelled)  => Some(Cancelled)
    case (Queued, Cancelled)  => Some(Cancelled)
    case _                    => None
