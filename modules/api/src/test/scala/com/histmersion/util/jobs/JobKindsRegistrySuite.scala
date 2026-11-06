package com.histmersion.util.jobs
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.jobs.JobInfo

import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.jobs.{JobKindEntry, JobKindMeaning, JobKindName, JobKinds}

class JobKindsRegistrySuite extends munit.FunSuite:
  private val a =
    JobKindEntry(JobInfo.Kind("alpha"), JobKindName("Alpha"), JobKindMeaning("Does alpha."), stoppable = com.histmersion.api.jobs.JobKindEntry.Stoppable(true))
  private val b =
    JobKindEntry(
      JobInfo.Kind("beta-run"),
      JobKindName("Beta"),
      JobKindMeaning("Does beta."),
      subject = JobDetail.Purpose.of
    )
  private val kinds = JobKinds(List(a, b))

  test("kinds in the order they were registered; looked up by kind or by the identifier as stored or typed"):
    assertEquals(kinds.kinds.map(k => k.value: String), List("alpha", "beta-run"))
    assertEquals(kinds.get(JobInfo.Kind("beta-run")), Some(b))
    assertEquals(kinds.parse(com.histmersion.api.jobs.JobKinds.Wire("  ALPHA ")), Some(a))
    assertEquals(kinds.parse(com.histmersion.api.jobs.JobKinds.Wire("gamma")), None)

  test("which kinds a person may stop"):
    assert(kinds.stoppable(com.histmersion.api.jobs.JobKinds.Wire("alpha")) && !kinds.stoppable(com.histmersion.api.jobs.JobKinds.Wire("beta-run")) && !kinds.stoppable(com.histmersion.api.jobs.JobKinds.Wire("gamma")))
    assertEquals(kinds.stoppableKinds, Set("alpha"))

  test("what a job is about: its source first, else the first registered reading of its detail"):
    val job = JobInfo(com.histmersion.api.SpatialHierarchyResult.Job.Id("j"), com.histmersion.api.jobs.JobKinds.Wire("alpha"), com.histmersion.api.jobs.JobInfo.Status("done"), detail = Some(JobDetail.Purpose(Some(com.histmersion.api.jobs.JobKinds.Subject("the map")))))
    assertEquals(kinds.subject(job), Some("the map"))
    assertEquals(kinds.subject(job.modify(_.citekey).setTo(Some(com.histmersion.api.library.Citekey("ck")))), Some("ck"))
    assertEquals(kinds.subject(job.modify(_.detail).setTo(None)), None)
