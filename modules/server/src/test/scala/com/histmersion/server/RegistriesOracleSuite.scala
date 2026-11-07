package com.histmersion.server

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.Wording

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.exchange.InitPlan
import com.histmersion.api.jobs.{JobActions, JobDetail, JobInfo}
import com.histmersion.api.maintenance.VacuumPlan
import com.histmersion.api.library.Acquire

/** Every job kind and every alias table the server registers, written as text to `src/test/resources/oracle/` and
  * compared byte for byte: for a job kind its identifier, the name a person reads, whether it can be paused and
  * cancelled, and what a job of it is about for each kind of detail; for an alias table the record's noun, its
  * identifier's word, the tables and columns a rename is resolved through, and the rule the surfaces state. Recorded on
  * the code before the registries existed, read from the registries since
  * (`com.histmersion.api.RegisteredJobKinds.all`, `Deps.aliasTables`); `HISTMERSION_UPDATE_ORACLE=1` rewrites it.
  */
class RegistriesOracleSuite extends munit.FunSuite:

  private val dir = Path.of("modules/server/src/test/resources/oracle")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private def check(name: String, actual: String): Unit =
    val file = dir.resolve(name)
    if update then
      Files.createDirectories(dir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  private val details: List[(String, JobDetail)] = List(
    "none" -> JobDetail.empty,
    "init" -> InitPlan.Detail(Some(InitPlan(gazetteer = com.histmersion.api.exchange.InitPlan.Gazetteer(true), periodPresets = com.histmersion.api.exchange.InitPlan.PeriodPresets(true), fetch = List(com.histmersion.api.exchange.InitPlan.Fetch("land"))))),
    "plan" -> com.histmersion.api.exchange.ExchangeJobKinds.PlanHash(Some(com.histmersion.api.exchange.MergePlan.PlanHash("abc123"))),
    "vacuum" -> VacuumPlan.Detail(Some(VacuumPlan(analyze = com.histmersion.api.maintenance.VacuumPlan.Analyze(true), tables = List(com.histmersion.api.maintenance.Vacuum.Request.Tables("sources"), com.histmersion.api.maintenance.Vacuum.Request.Tables("places"))))),
    "media" -> com.histmersion.api.media.ImportJobKinds.Media(Some(com.histmersion.api.jobs.JobKinds.Subject("pictures of 3 places"))),
    "events" -> com.histmersion.api.catalogue.EventsJobKinds.Catalogue(Some(com.histmersion.api.jobs.JobKinds.Subject("gvp: 10 parts"))),
    "about" -> JobDetail.Purpose(Some(com.histmersion.api.jobs.JobKinds.Subject("the vector map of crete"))),
    "recheck" -> Acquire.Checks.RecheckJob.Detail(
      Some(Acquire.Checks.RecheckJob(all = com.histmersion.api.library.Acquire.Checks.RecheckJob.All(true), upTo = com.histmersion.api.library.Acquire.Checks.RecheckJob.UpTo(7), since = com.histmersion.api.library.Acquire.Checks.RecheckJob.Since("2026-10-01")))
    )
  )

  test("job-kinds.txt: every job kind, its name, its controls and its subjects"):
    val lines = com.histmersion.api.RegisteredJobKinds.all.kinds.flatMap { k =>
      val head = s"${k.value}\t${Wording.jobKindOf(k)}\tstoppable=${JobActions.Stoppable.contains(k)}"
      val subjects = details.map { (name, d) =>
        val j = JobInfo(id = com.histmersion.api.SpatialHierarchyResult.Job.Id("j"), kind = com.histmersion.api.jobs.JobKinds.Wire(k.value), status = com.histmersion.api.jobs.JobInfo.Status("done"), detail = Some(d))
        s"  $name\t${JobDetail.subject(j).getOrElse("-")}"
      }
      val withCitekey = JobInfo(id = com.histmersion.api.SpatialHierarchyResult.Job.Id("j"), kind = com.histmersion.api.jobs.JobKinds.Wire(k.value), status = com.histmersion.api.jobs.JobInfo.Status("done"), citekey = (Some("hattusa_1990")).map(com.histmersion.api.library.Citekey.apply))
      head :: subjects ::: List(s"  citekey\t${JobDetail.subject(withCitekey).getOrElse("-")}")
    }
    val unknown = s"unknown\t${Wording.jobKindOf(com.histmersion.api.jobs.JobKinds.Wire("some-new-kind"))}\tstoppable=${JobActions.Stoppable
        .contains(com.histmersion.api.jobs.JobKinds.Wire("some-new-kind"))}"
    check("job-kinds.txt", (lines :+ unknown).mkString("", "\n", "\n"))

  test("alias-tables.txt: every alias table, its words and its columns"):
    val lines = Deps.aliasTables.tables.map { t =>
      val k = t.kind
      s"${k.noun}\t${k.idWord}\t${t.table}.${t.idColumn}\t${t.aliasTable}.alias -> ${t.targetColumn}\n" +
        s"  rule\t${Aliases.rule(k)}\n  followed\t${Aliases.followed(k, com.histmersion.api.aliases.Aliases.Id("old_one"), com.histmersion.api.aliases.Aliases.Id("new_one")).getOrElse("-")}"
    }
    val bundles =
      com.histmersion.api.exchange.Bundle.Kind.values.toList.map(b =>
        s"bundle $b\t${AliasKinds.of(b).fold("-")(_.noun)}"
      )
    check("alias-tables.txt", (lines ++ bundles).mkString("", "\n", "\n"))
