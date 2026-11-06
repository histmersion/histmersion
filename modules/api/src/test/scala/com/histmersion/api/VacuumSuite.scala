package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import io.circe.parser.parse
import com.histmersion.api.jobs.{JobDetail, JobInfo}
import com.histmersion.api.library.IngestMarkdown
import com.histmersion.api.maintenance.{Vacuum, VacuumPlan}

/** `histmersion maintenance vacuum` (#1445): which tables a request names, the statement for each, and how a job says
  * what it does.
  */
class VacuumSuite extends munit.FunSuite:

  private val defaults = List("places", "place_names", "finding_places")
  private val existing = Set("places", "place_names", "finding_places", "findings", "events")

  test("no table named: the configured ones; named ones are trimmed, lower-cased, de-duplicated, comma lists split"):
    assertEquals(Vacuum.plan(Vacuum.Request(), defaults, existing), Right(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(false), defaults)))
    assertEquals(
      Vacuum.plan(Vacuum.Request(analyze = com.histmersion.api.maintenance.Vacuum.Request.Analyze(true), tables = List(com.histmersion.api.maintenance.Vacuum.Request.Tables(" Findings "), com.histmersion.api.maintenance.Vacuum.Request.Tables("events,findings"))), defaults, existing),
      Right(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(true), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("findings"), com.histmersion.api.maintenance.Vacuum.Request.Tables("events"))))
    )

  test("a name that is no identifier, or no table of the store, is refused — and so is an empty list"):
    def refused(tables: List[String], d: List[String] = defaults) =
      Vacuum.plan(Vacuum.Request(tables = tables), d, existing).left.map(e => (e.code, e.message))
    assertEquals(refused(List("places; DROP TABLE x")).left.map(_._1), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(List("pg_catalog.pg_class")).left.map(_._1), Left(Some(Write.CodeInvalid)))
    assertEquals(refused(List("nope")), Left((Some(Write.CodeInvalid), "no such table: nope")))
    assertEquals(refused(Nil, Nil).left.map(_._1), Left(Some(Write.CodeInvalid)))

  test("the statement quotes the table; ANALYZE only when asked"):
    assertEquals(Vacuum.statement(com.histmersion.api.maintenance.Vacuum.Table("place_names"), analyze = com.histmersion.api.maintenance.Vacuum.Request.Analyze(false)), "VACUUM \"place_names\"")
    assertEquals(Vacuum.statement(com.histmersion.api.maintenance.Vacuum.Table("place_names"), analyze = com.histmersion.api.maintenance.Vacuum.Request.Analyze(true)), "VACUUM (ANALYZE) \"place_names\"")

  test("a job says what it vacuums, and each table's line what it reclaimed"):
    assertEquals(
      Vacuum.describe(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(true), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("places"), com.histmersion.api.maintenance.Vacuum.Request.Tables("place_names")))),
      "VACUUM (ANALYZE) of places, place_names"
    )
    assertEquals(Vacuum.describe(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(false), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("places")))), "VACUUM of places")
    assertEquals(
      Vacuum.tableLine(com.histmersion.api.maintenance.Vacuum.Table("place_names"), com.histmersion.api.maintenance.Vacuum.DeadRows(1237921), com.histmersion.api.maintenance.Vacuum.DeadRows(0), com.histmersion.api.ByteCount(3738000000L), com.histmersion.api.ByteCount(3738000000L), com.histmersion.api.maintenance.Storage.Report.DurationMillis(81234)),
      "place_names: 1237921 → 0 dead rows, 3.7 GB → 3.7 GB (81.2 s)"
    )
    val job = JobInfo(
      com.histmersion.api.SpatialHierarchyResult.Job.Id("j1"),
      com.histmersion.api.jobs.JobKinds.Wire(Vacuum.JobKind),
      com.histmersion.api.jobs.JobInfo.Status("running"),
      None,
      None,
      None,
      None,
      com.histmersion.api.jobs.JobInfo.CreatedAt("t"),
      com.histmersion.api.jobs.JobInfo.UpdatedAt("t"),
      detail = Some(VacuumPlan.Detail(Some(VacuumPlan(com.histmersion.api.maintenance.VacuumPlan.Analyze(false), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("places"))))))
    )
    assertEquals(JobDetail.subject(job), Some("VACUUM of places"))
    assert(IngestMarkdown.job(job).contains("- **vacuums:** VACUUM of places"))

  test("a request body: unknown keys refused, absent ones defaulted"):
    assertEquals(parse("""{}""").flatMap(_.as[Vacuum.Request]), Right(Vacuum.Request()))
    assertEquals(
      parse("""{"analyze":true,"tables":["places"]}""").flatMap(_.as[Vacuum.Request]),
      Right(Vacuum.Request(com.histmersion.api.maintenance.Vacuum.Request.Analyze(true), List(com.histmersion.api.maintenance.Vacuum.Request.Tables("places"))))
    )
    assert(parse("""{"full":true}""").flatMap(_.as[Vacuum.Request]).isLeft)
