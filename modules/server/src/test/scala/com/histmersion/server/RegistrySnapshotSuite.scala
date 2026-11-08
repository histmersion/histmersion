package com.histmersion
package server

import com.histmersion.api.aliases.AliasKinds

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import com.histmersion.util.data.checks.CheckOrder
import com.histmersion.api.checks.Lint
import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.server.agents.ServerEngineSite
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.domain.library.collections.CollectionsService
import com.histmersion.domain.library.collections.RefusingCollectionLibrary

/** What the composition registers, written as text to `src/test/resources/oracle/registries/` and compared byte for
  * byte, recorded on the code before the transition adapters and `Handlers` went (slice 12, part 2): every owner's data
  * contribution in the registry's order (its tables, stores, backup share, carried kinds with their import steps, merge
  * rules, mirror notes, caches, upkeep and checks), the lint order, the alias kinds; and, on a server's composition,
  * the engine's registry (workflows, agent tools, manager kinds, objectives, the approval kinds an effect applies), the
  * REST routes in the order they are matched (the MCP tools' order is `ContractSnapshotSuite`'s `mcp-tools.json`).
  * `HISTMERSION_UPDATE_ORACLE=1` rewrites it. No database: the composition runs over one that refuses every connection,
  * as `ContractSnapshotSuite`'s does.
  */
class RegistrySnapshotSuite extends munit.FunSuite:

  private val dir = Path.of("modules/server/src/test/resources/oracle/registries")
  private val update = sys.env.get("HISTMERSION_UPDATE_ORACLE").contains("1")

  private def check(name: String, actual: String): Unit =
    val file = dir.resolve(name)
    if update then
      Files.createDirectories(dir)
      val _ = Files.writeString(file, actual, UTF_8)
    else
      assert(Files.exists(file), s"$file is missing; write it with HISTMERSION_UPDATE_ORACLE=1")
      assertNoDiff(actual, Files.readString(file, UTF_8))

  test("data.txt: every owner's data contribution, the lint order and the alias kinds"):
    val data = Deps.data
    val owners = data.all.flatMap { c =>
      val share = c.backup
      List(
        s"owner ${c.owner.value}",
        s"  tables\t${c.tables.map(_.name.value).mkString(",")}",
        s"  stores\t${c.stores.map(_.toString).mkString(",")}",
        s"  backup\tstores=${share.stores.map(_.toString).mkString(",")} guarded=${share.guarded
            .map(g => s"${g.table.value}:${g.noun}")
            .mkString(",")}"
      ) ++ c.carried.map { k =>
        val steps = k.rowsIn.map(r => s"${r.stage}@${r.rank.value}").mkString(",")
        s"  carried\t${k.entry.value}\trecord=${k.record.fold("-")(_.toString)}\ttable=${k.table
            .fold("-")(_.value)}\tsteps=$steps"
      } ++ c.mergeRules.map { r =>
        s"  merge\t${r.kind}\tlabel=${r.ledgerLabel.getOrElse("-")}\treferable=${r.referable}\tplanning=${r.planning.isDefined}"
      } ++ c.mirror.map(m => s"  mirror\t${m.records}\t${m.placement}") ++ c.caches.map(k =>
        s"  cache\t${k.name.value}\t${k.label}\t${k.holding}\tstale=${k.staleAfter.mkString(",")}"
      ) ++ c.maintenance.map(t => s"  task\t${t.name.value}") ++ c.checks.map(r =>
        s"  check\t${r.rule.name}\tfix=${r.fix.isDefined}"
      )
    }
    val order = CheckOrder(Lint.names)
    val fixes = order.fixing(data.checks).filter(_.fix.isDefined).map(_.rule.name)
    val lint = List(s"lint report\t${order.report.mkString(",")}", s"lint fixes\t${fixes.mkString(",")}")
    val aliases = AliasKinds.all.map(k => s"alias kind\t${k.noun}\t${k.idWord}")
    check("data.txt", (owners ++ lint ++ aliases).mkString("", "\n", "\n"))

  private val loaded = AppConfig
    .loadWithUserText(org.ekrich.config.ConfigFactory.empty, "")
    .fold(e => throw AssertionError(e), identity)

  test("engine.txt and routes.txt: the engine's registry and the REST routes, in their order"):
    val deps = Deps(loaded.config, RefusingDataSource)
    val h = ServerWiring(deps, OfflineConverter())
    val reg = h.agents.workflows
    val workflows = reg.definitions.map { d =>
      val tools = d.roles.map(r => s"${r.wire}:${d.allowedTools(r).toList.map(_.value).sorted.mkString("+")}")
      val outputs = d.outputs != OutputComparison.none
      s"workflow\t${d.id.value}\tv${d.version}\t${d.target.wire}\t${tools.mkString(",")}\toutputs=$outputs"
    }
    val tools = reg.tools.map(t => s"tool\t${t.name.value}")
    val kinds = reg.managerKinds.map(k =>
      s"manager kind\t${k.kind.wire}\titems=${k.items.mkString(",")}\tcron=${k.cron.getOrElse("-")}"
    )
    val objectives = s"objectives\t${reg.objectives.size}"
    val effects = ServerEngineSite(deps, h)
      .approvalEffects
      .subjectKinds
      .toList
      .map(_.toString)
      .sorted
      .mkString(",")
    val roster = h.agents.definitions.roster
    val rosterLines = List(
      s"roster tools\t${roster.tools.map(_.value).mkString(",")}",
      s"roster roles\t${roster.roles.map(_.wire).mkString(",")}"
    )
    check(
      "engine.txt",
      (workflows ++ tools ++ kinds ++ List(objectives, s"approval subject kinds\t$effects") ++ rosterLines)
        .mkString("", "\n", "\n")
    )
    val acq = AcquisitionTesting.controllers(
      deps,
      discovery = ???,
      citations = ???,
      oa = ???,
      wiki = ???,
      retractionWatch = ???,
      jobs = AcquisitionTesting.skeleton(deps)
    )
    val collections = CollectionsControllers(
      CollectionsService.InMemory(
        java.util.concurrent.atomic.AtomicReference(Nil),
        RefusingCollectionLibrary(),
        () => scala.collection.immutable.ListMap.empty,
        CollectionsService.Limit(8)
      ),
      deps.maintenance
    )
    val routes = HttpApi(deps, h, acq, collections).endpoints.map(e => s"route\t${e.endpoint.show}")
    check("routes.txt", routes.mkString("", "\n", "\n"))
