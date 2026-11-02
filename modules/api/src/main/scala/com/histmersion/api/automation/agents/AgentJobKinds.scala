package com.histmersion.api.automation.agents

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The engine's jobs, as it registers them: an agent run, which its own runner pauses, resumes and cancels. */
object AgentJobKinds:
  val Agent: JobKindEntry =
    JobKindEntry.of(
      JobKind("agent"),
      "Agent run",
      "An agent run; the agent runs window shows its steps.",
      ownControl = true
    )
