package com.histmersion.util.jobs

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.catalogue.SkippedPeriod
import com.histmersion.api.maintenance.VacuumPlan
import io.circe.parser.decode
import io.circe.syntax.*

/** A job's detail kept as its fields' JSON: the wire shows every registered field in the registry's order, a value read
  * back in any key order is written in its field's terms, and a detail holds only what differs from the defaults.
  */
class JobDetailSuite extends munit.FunSuite:

  test("an empty detail shows every field with its default, in the registry's order"):
    val keys = JobDetail.empty.asJson.asObject.map(_.keys.toList).getOrElse(Nil)
    assertEquals(keys, registered)
    assertEquals(JobDetail.empty.asJson.noSpaces.take(40), """{"init":null,"skippedPeriods":[],"skippe""")

  private def registered = com.histmersion.api.RegisteredJobKinds.all.details.map(_.name)

  test("a value read back in another key order (as jsonb keeps it) is written in its field's order"):
    val stored = """{"vacuum": {"tables": ["sources"], "analyze": true}, "skippedPeriodCount": 0, "init": null}"""
    val d = decode[JobDetail](stored).fold(e => fail(e.getMessage), identity)
    assertEquals(d, VacuumPlan.Detail(Some(VacuumPlan(analyze = com.histmersion.api.maintenance.VacuumPlan.Analyze(true), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("sources"))))))
    assert(d.asJson.noSpaces.contains(""""vacuum":{"analyze":true,"tables":["sources"]}"""), d.asJson.noSpaces)

  test("defaults and unknown keys are not held, so a detail of defaults is the empty one"):
    val d = decode[JobDetail]("""{"skippedPeriods": [], "about": null, "later": 1}""").toOption
    assertEquals(d, Some(JobDetail.empty))

  test("a field set, changed and read; a value of the wrong shape fails the whole detail"):
    val d = InitPlan.Detail(Some(InitPlan(gazetteer = com.histmersion.api.exchange.InitPlan.Gazetteer(true)))).change(SkippedPeriod.SkippedCount)(c => com.histmersion.api.catalogue.Period.ImportResult.SkippedCount(c.value + 2))
    assertEquals(SkippedPeriod.SkippedCount.of(d), 2)
    assertEquals(InitPlan.Detail.of(d).map(_.gazetteer), Some(true))
    assertEquals(SkippedPeriod.Skipped.of(d), Nil)
    assert(decode[JobDetail]("""{"skippedPeriodCount": "two"}""").isLeft)
