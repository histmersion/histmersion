package com.histmersion.api.automation
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.util.jobs.Job

/** The engine's jobs, as it registers them: an agent run, which its own runner pauses, resumes and cancels. */
object AgentJobKinds:
  val Agent: JobKindEntry =
    JobKindEntry.of(
      Job.Kind("agent"),
      "Agent run",
      "An agent run; the agent runs window shows its steps.",
      ownControl = true
    )
