package com.histmersion.util.jobs

import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.values.JobKind
import com.histmersion.api.jobs.{JobKindEntry, JobKindMeaning, JobKindName, JobKinds}

class JobKindsRegistrySuite extends munit.FunSuite:
  private val a = JobKindEntry(JobKind("alpha"), JobKindName("Alpha"), JobKindMeaning("Does alpha."), stoppable = true)
  private val b =
    JobKindEntry(JobKind("beta-run"), JobKindName("Beta"), JobKindMeaning("Does beta."), subject = JobDetail.Purpose.of)
  private val kinds = JobKinds(List(a, b))

  test("kinds in the order they were registered; looked up by kind or by the identifier as stored or typed"):
    assertEquals(kinds.kinds.map(k => k.value: String), List("alpha", "beta-run"))
    assertEquals(kinds.get(JobKind("beta-run")), Some(b))
    assertEquals(kinds.parse("  ALPHA "), Some(a))
    assertEquals(kinds.parse("gamma"), None)

  test("which kinds a person may stop"):
    assert(kinds.stoppable("alpha") && !kinds.stoppable("beta-run") && !kinds.stoppable("gamma"))
    assertEquals(kinds.stoppableKinds, Set("alpha"))

  test("what a job is about: its source first, else the first registered reading of its detail"):
    val job = JobInfo("j", "alpha", "done", detail = Some(JobDetail.Purpose(Some("the map"))))
    assertEquals(kinds.subject(job), Some("the map"))
    assertEquals(kinds.subject(job.copy(citekey = Some("ck"))), Some("ck"))
    assertEquals(kinds.subject(job.copy(detail = None)), None)
