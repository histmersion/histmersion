package com.histmersion
package api.automation
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The engine's jobs, as it registers them: an agent run, which its own runner pauses, resumes and cancels. */
object AgentJobKinds:
  val Agent: JobKindEntry =
    JobKindEntry.of(
      JobInfo.Kind("agent"),
      JobKinds.Wire("Agent run"),
      JobKindMeaning("An agent run; the agent runs window shows its steps."),
      ownControl = JobKindEntry.OfOwnControl(true)
    )
