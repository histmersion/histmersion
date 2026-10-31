package com.histmersion.server

import com.histmersion.api.*

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

/** Every job kind and every alias table the server registers, written as text to `src/test/resources/oracle/` and
  * compared byte for byte: for a job kind its identifier, the name a person reads, whether it can be paused and
  * cancelled, and what a job of it is about for each kind of detail; for an alias table the record's noun, its
  * identifier's word, the tables and columns a rename is resolved through, and the rule the surfaces state. Recorded on
  * the code before the registries existed, read from the registries since (`JobKindsAdapter.all`, `Deps.aliasTables`);
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites it.
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
    "none" -> JobDetail(),
    "init" -> JobDetail(init = Some(InitPlan(gazetteer = true, periodPresets = true, fetch = List("land")))),
    "plan" -> JobDetail(planHash = Some("abc123")),
    "vacuum" -> JobDetail(vacuum = Some(VacuumPlan(analyze = true, tables = List("sources", "places")))),
    "media" -> JobDetail(media = Some("pictures of 3 places")),
    "events" -> JobDetail(events = Some("gvp: 10 parts")),
    "about" -> JobDetail(about = Some("the vector map of crete")),
    "recheck" -> JobDetail(recheck = Some(AcquireChecks.RecheckJob(all = true, upTo = 7, since = "2026-10-01")))
  )

  test("job-kinds.txt: every job kind, its name, its controls and its subjects"):
    val lines = JobKindsAdapter.all.kinds.flatMap { k =>
      val head = s"${k.value}\t${Wording.jobKindOf(k.value)}\tstoppable=${JobActions.Stoppable.contains(k.value)}"
      val subjects = details.map { (name, d) =>
        val j = JobInfo(id = "j", kind = k.value, status = "done", detail = Some(d))
        s"  $name\t${JobDetail.subject(j).getOrElse("-")}"
      }
      val withCitekey = JobInfo(id = "j", kind = k.value, status = "done", citekey = Some("hattusa_1990"))
      head :: subjects ::: List(s"  citekey\t${JobDetail.subject(withCitekey).getOrElse("-")}")
    }
    val unknown = s"unknown\t${Wording.jobKindOf("some-new-kind")}\tstoppable=${JobActions.Stoppable
        .contains("some-new-kind")}"
    check("job-kinds.txt", (lines :+ unknown).mkString("", "\n", "\n"))

  test("alias-tables.txt: every alias table, its words and its columns"):
    val lines = Deps.aliasTables.tables.map { t =>
      val k = t.kind
      s"${k.noun}\t${k.idWord}\t${t.table}.${t.idColumn}\t${t.aliasTable}.alias -> ${t.targetColumn}\n" +
        s"  rule\t${Aliases.rule(k)}\n  followed\t${Aliases.followed(k, "old_one", "new_one").getOrElse("-")}"
    }
    val bundles =
      com.histmersion.values.BundleKind.values.toList.map(b =>
        s"bundle $b\t${AliasKindsAdapter.of(b).fold("-")(_.noun)}"
      )
    check("alias-tables.txt", (lines ++ bundles).mkString("", "\n", "\n"))
