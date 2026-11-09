package com.histmersion
package cli

import com.histmersion.api.problem.Text.sentence
import com.histmersion.api.library.RepairAcquire
import com.monovore.decline.Command
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.syntax.*
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.api.automation.Goal
import com.histmersion.api.library.Doi
import com.histmersion.api.automation.Managers

/** `histmersion agent manager new review|enrich|seed …`, `agent enqueue <kind> <target>` and the --json flags parse
  * (phase 2 of docs/research-process-managers.md); a bad setting is refused before any request is sent.
  */
class ManagerCmdSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private val manager = Command("t", "test")(ManagerCmd.manager)
  private val enqueue = Command("t", "test")(ManagerCmd.enqueue)
  private val managers = Command("t", "test")(ManagerCmd.managers)

  test("new seed --goal, new enrich, with settings and --json"):
    assert(
      manager
        .parse(
          List("manager", "new", "seed", "--goal", "bronze-age-tin", "--config", "min-sources=3", "--json"),
          Map.empty
        )
        .isRight
    )
    assert(manager.parse(List("manager", "new", "enrich", "--config", "max-runs=2"), Map.empty).isRight)
    assert(
      manager.parse(List("manager", "seed:bronze-age-tin", "items", "--status", "held", "--json"), Map.empty).isRight
    )
    assert(manager.parse(List("manager", "review", "status", "--json"), Map.empty).isRight)
    assert(manager.parse(List("manager", "seed:x", "enqueue", "propose", "x"), Map.empty).isRight)

  test("enqueue takes a kind and a target; managers takes --json"):
    assert(enqueue.parse(List("enqueue", "extend", "sea_peoples"), Map.empty).isRight)
    assert(enqueue.parse(List("enqueue", "enrich", "bronze-age-tin", "--priority", "3", "--json"), Map.empty).isRight)
    assert(enqueue.parse(List("enqueue", "seed"), Map.empty).isLeft, "a target is needed")
    assert(managers.parse(List("managers", "--json"), Map.empty).isRight)

  test("the agent command offers enqueue"):
    val agent = Command("histmersion", "test")(AgentCmd.command)
    assert(agent.parse(List("agent", "enqueue", "review", "x"), Map.empty).isRight)

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
    val cmd = Command("t", "test")(ManagerCmd.autopilot)
    assert(cmd.parse(List("autopilot", "focus", "pin", "x", "--json"), Map.empty).isRight)
    assert(cmd.parse(List("autopilot", "focus"), Map.empty).isRight)
    assert(cmd.parse(List("autopilot"), Map.empty).isRight)
    val extra = cmd.parse(List("autopilot", "on", "x"), Map.empty).fold(h => fail(h.toString), identity)
    assert(
      extra(new Client(Client.ServerUrl("http://127.0.0.1:1"))).left.exists(_.exitCode == ExitCode.Usage),
      "on takes no further argument"
    )

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
      val cmd = Command("t", "test")(ManagerCmd.autopilot)
      val f =
        cmd.parse(List("autopilot", "repair-acquire", "--dry-run"), Map.empty).fold(h => fail(h.toString), identity)
      val out = ByteArrayOutputStream()
      val r = Console.withOut(out)(f(new Client(Client.ServerUrl(s"http://127.0.0.1:${srv.getAddress.getPort}"))))
      assertEquals(r, Right(()))
      assert(
        seen.peek().startsWith("POST /api/agents/autopilot/repair-acquire") && seen.peek().contains("\"dryRun\":true"),
        seen.peek()
      )
      val text = out.toString(UTF_8)
      assert(text.contains("would move 1 legal open-access work") && text.contains("#7 https://doi.org/10.1/x"), text)
      val bad = cmd.parse(List("autopilot", "repair"), Map.empty).fold(h => fail(h.toString), identity)
      assert(
        bad(new Client(Client.ServerUrl("http://127.0.0.1:1"))).left
          .exists(_.message.sentence.contains("repair-acquire"))
      )
    finally srv.stop(0)
