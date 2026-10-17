package com.histmersion.cli

import com.monovore.decline.Command

/** `histmersion agent manager new review|enrich|seed …`, `agent enqueue <kind> <target>` and the --json
  * flags parse (phase 2 of docs/research-process-managers.md); a bad setting is refused before any
  * request is sent. */
class ManagerCmdSuite extends munit.FunSuite:

  private val manager = Command("t", "test")(ManagerCmd.manager)
  private val enqueue = Command("t", "test")(ManagerCmd.enqueue)
  private val managers = Command("t", "test")(ManagerCmd.managers)

  test("new seed --goal, new enrich, with settings and --json"):
    assert(manager.parse(List("manager", "new", "seed", "--goal", "bronze-age-tin", "--config", "min-sources=3", "--json"), Map.empty).isRight)
    assert(manager.parse(List("manager", "new", "enrich", "--config", "max-runs=2"), Map.empty).isRight)
    assert(manager.parse(List("manager", "seed:bronze-age-tin", "items", "--status", "held", "--json"), Map.empty).isRight)
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
