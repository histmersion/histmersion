package com.histmersion.api.automation
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The engine's jobs, as it registers them: an agent run, which its own runner pauses, resumes and cancels. */
object AgentJobKinds:
  val Agent: JobKindEntry =
    JobKindEntry.of(
      JobInfo.Kind("agent"),
      _root_.com.histmersion.api.jobs.JobKindEntry.OfName("Agent run"),
      _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("An agent run; the agent runs window shows its steps."),
      ownControl = true
    )
