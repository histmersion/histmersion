# 0021. The stall watchdog interrupts a run before it kills its processes, and a stalled run checkpoints nothing

- Status: accepted
- Date: 2026-11-21

## Context

The stall watchdog (`agents.watchdog`) stops a run that showed no sign of life for `stall-minutes`: it marks the run
stalled, kills the CLI processes the run started, and interrupts the run's thread. It used to kill first and interrupt
after. A killed CLI's exit (status 143, no output) could then reach the run thread before the interrupt: the CLI call
returned normally, the code step recorded that empty result as done, and the resumed run replayed the checkpoint instead
of starting the CLI again. On Linux CI runners, where a killed process exits fast, this failed most runs of
AgentEngineSuite's watchdog test (CI 44903898937, 44900508629, 44889089633). The late interrupt also landed in the
writes that followed and broke their JDBC connections ("Closed by interrupt").

## Decision

- The watchdog sets the run's halt first, interrupts the run's thread second, and kills its processes last. A run
  blocked waiting for its CLI leaves the step by its `InterruptedException` (killing the CLI itself); the kill after it
  covers a process the run is not waiting for.
- A code step whose run the watchdog has marked stalled records no checkpoint, whatever its body returned: the run
  settles as the watchdog decided, and the resumed run does the step again.

## Consequences

A step that truly finished in the instant the watchdog fired runs once more on resume; a stall means minutes of silence,
so that coincidence is far rarer than the race it replaces. A run that is not blocked when interrupted (computing, or in
a database call) still meets the interrupt at its next blocking call, as a cancel does.

## Code

`Engine.checkStalls` and `Engine.stalled`; `RunCtx.code`. Tests: AgentEngineSuite, "watchdog: a run with no sign of life
…" and "watchdog: a step that comes back from a stalled run is not checkpointed …".
