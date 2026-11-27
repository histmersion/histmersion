package com.histmersion
package cli
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.library.RepairAcquire
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Doi
import com.histmersion.api.automation.Managers
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion agent manager new review|enrich|seed …`, `agent enqueue <kind> <target>` and the --json flags parse
  * (phase 2 of docs/research-process-managers.md); a bad setting is refused before any request is sent.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class ManagerCmdSuite extends munit.FunSuite:

  private given console: util.console.Console = util.console.Console.standard

  private def parses(args: String*) = Parsing.parses("agent" +: args*)

  test("new seed --goal, new enrich, with settings and --json"):
    assert(parses("manager", "new", "seed", "--goal", "bronze-age-tin", "--config", "min-sources=3", "--json"))
    assert(parses("manager", "new", "enrich", "--config", "max-runs=2"))
    assert(parses("manager", "seed:bronze-age-tin", "items", "--status", "held", "--json"))
    assert(parses("manager", "review", "status", "--json"))
    assert(parses("manager", "seed:x", "enqueue", "propose", "x"))
    assert(parses("manager", "review"), "a manager alone shows it")
    assertEquals(
      Parsing.parse("agent", "manager", "seed:x", "pause", "--runs"),
      Right(ManagerCmd.Command.Manager("seed:x", ManagerCmd.ManagerVerb.Pause(true)))
    )
    assert(!parses("manager", "items"), "a verb needs the manager's id")

  test("enqueue takes a kind and a target; managers takes --json; the agent command offers enqueue"):
    assert(parses("enqueue", "extend", "sea_peoples"))
    assert(parses("enqueue", "enrich", "bronze-age-tin", "--priority", "3", "--json"))
    assert(!parses("enqueue", "seed"), "a target is needed")
    assert(parses("managers", "--json"))
    assert(parses("enqueue", "review", "x"))

  test("the scheduler: status by default; queue, why <item>, strategy <name>"):
    assert(parses("scheduler"))
    assert(parses("scheduler", "--limit", "5", "--json"))
    assert(parses("scheduler", "queue", "--limit", "5"))
    assert(parses("scheduler", "why", "x"))
    assert(parses("scheduler", "strategy", "goal-first"))
    assert(!parses("scheduler", "why"))
    assert(!parses("scheduler", "bogus"))

  test("autopilot focus: list, pin, unpin, priority, max parse into the request; a malformed one is a usage error"):
    import com.histmersion.api.automation.Autopilot.FocusSet
    assertEquals(ManagerCmd.focusRequest(Nil), Right(None))
    assertEquals(ManagerCmd.focusRequest(List("list")), Right(None))
    assertEquals(
      ManagerCmd.focusRequest(List("pin", "bronze-age")),
      Right(Some(FocusSet(FocusSet.Action("pin"), Some(Goal.Slug("bronze-age")), by = Some(FocusSet.By("cli")))))
    )
    assertEquals(
      ManagerCmd.focusRequest(List("unpin", "x")).map(_.map(_.action)),
      Right(Some(FocusSet.Action("unpin")))
    )
    assertEquals(
      ManagerCmd.focusRequest(List("priority", "x", "4")),
      Right(
        Some(
          FocusSet(FocusSet.Action("priority"), Some(Goal.Slug("x")), Some(FocusSet.Value(4)), Some(FocusSet.By("cli")))
        )
      )
    )
    assertEquals(
      ManagerCmd.focusRequest(List("max", "3")),
      Right(Some(FocusSet(FocusSet.Action("max"), value = Some(FocusSet.Value(3)), by = Some(FocusSet.By("cli")))))
    )
    assertEquals(
      ManagerCmd.focusRequest(List("max", "default")),
      Right(Some(FocusSet(FocusSet.Action("max"), by = Some(FocusSet.By("cli")))))
    )
    List(List("pin"), List("max", "-1"), List("max", "many"), List("priority", "x"), List("bogus")).foreach { a =>
      assert(ManagerCmd.focusRequest(a).left.exists(_.exitCode == ExitCode.Usage), a)
    }
    assert(parses("autopilot", "focus", "pin", "x", "--json"))
    assert(parses("autopilot", "focus"))
    assert(parses("autopilot"))
    assert(!parses("autopilot", "on", "x"), "on takes no further argument")
    assert(!parses("autopilot", "focus", "max", "-1"), "a malformed focus is refused while parsing")

  test(
    "autopilot repair-acquire [--dry-run] posts the request and prints what it did; an unknown verb is a usage error"
  ):
    val seen = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val answer = RepairAcquire.RepairResult(
      dryRun = RepairAcquire.RepairResult.DryRun(true),
      moved = RepairAcquire.RepairResult.Moved(1),
      untouched = RepairAcquire.RepairResult.Untouched(3),
      rows = List(
        RepairAcquire.RepairRow(
          RepairAcquire.RepairRow.Item(7),
          Doi("10.1/x"),
          List(RepairAcquire.RepairRow.Goals("g")),
          RepairAcquire.RepairRow.Watcher("watch:g"),
          RepairAcquire.RepairRow.Action(RepairAcquire.Move),
          RepairAcquire.RepairRow.Detail("legal open access")
        )
      ),
      backlog = Managers.ManagerInfo.Backlog(1),
      backlogDays = Some(Managers.ManagerInfo.BacklogDays(1))
    )
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        seen.add(
          s"${ex.getRequestMethod} ${ex.getRequestURI.getPath} ${new String(ex.getRequestBody.readAllBytes(), UTF_8)}"
        )
        val bytes = answer.asJson.noSpaces.getBytes(UTF_8)
        ex.getResponseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.length.toLong)
        ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    try
      val f = Parsing.parse("agent", "autopilot", "repair-acquire", "--dry-run").fold(h => fail(h.toString), identity)
      val out = ByteArrayOutputStream()
      val r = Console.withOut(out)(f.run(new Client(Loopback.url(srv.getAddress.getPort))))
      assertEquals(r, Right(()))
      assert(
        seen.peek().startsWith("POST /api/agents/autopilot/repair-acquire") && seen.peek().contains("\"dryRun\":true"),
        seen.peek()
      )
      val text = out.toString(UTF_8)
      assert(text.contains("would move 1 legal open-access work") && text.contains("#7 https://doi.org/10.1/x"), text)
      assert(!parses("autopilot", "repair"), "an unknown verb is refused")
    finally srv.stop(0)
