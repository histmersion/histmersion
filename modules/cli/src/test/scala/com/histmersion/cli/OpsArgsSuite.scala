package com.histmersion
package cli

import com.histmersion.api.automation.queue.Docket
import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.cli.Cli.Following.{Follow, Return}
import com.histmersion.cli.Cli.Writes.{Apply, DryRun}
import com.histmersion.cli.Ops.QueueCmd
import com.histmersion.api.automation.Queue
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

/** `histmersion queue …`: each list and each verb is a subcommand (`queue acquire add "…"`, `queue done 3`, `queue
  * acquire recheck`), parsed into [[QueueCmd]].
  */
@tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
class OpsArgsSuite extends munit.FunSuite:

  private val json = Cli.JsonOutput.Text

  private def parse(args: String*) = Parsing.parse("queue" +: args*)

  test("`queue <list>` shows that list, --all with the done items"):
    assertEquals(parse("acquire"), Right(QueueCmd.Show(Docket.Label("acquire"), false, json)))
    assertEquals(parse("tasks"), Right(QueueCmd.Show(Docket.Label("tasks"), false, json)))
    assertEquals(parse("log", "--json"), Right(QueueCmd.Show(Docket.Label("log"), false, Cli.JsonOutput.Json)))
    assertEquals(parse("tasks", "--all"), Right(QueueCmd.Show(Docket.Label("tasks"), true, json)))

  test("`queue <list> add \"text\"` adds, with --topic and --reason when given"):
    assertEquals(
      parse("acquire", "add", "Eller, Myth of Matriarchal Prehistory"),
      Right(
        QueueCmd.Add(
          Docket.Label("acquire"),
          QueueItem.Text("Eller, Myth of Matriarchal Prehistory"),
          None,
          None,
          AgentIdentity.Nobody,
          json
        )
      )
    )
    parse("tasks", "add", "enrich x", "--topic", "Backlog", "--reason", "why") match
      case Right(QueueCmd.Add(list, text, topic, Some(reason), AgentIdentity.Nobody, `json`)) =>
        assertEquals(
          (list, text, topic),
          (Docket.Label("tasks"), QueueItem.Text("enrich x"), Some(QueueItem.Topic("Backlog")))
        )
        assertEquals(reason, Queue.Rationale("why"))
      case other => fail(s"${other.toString}")

  test("`add --by-…` names the automated caller: each flag one part of its identity"):
    parse(
      "tasks",
      "add",
      "enrich x",
      "--by-plugin",
      "research-workspace",
      "--by-workflow",
      "review-article",
      "--by-agent",
      "article-skeptic",
      "--by-provider",
      "anthropic",
      "--by-model",
      "claude-opus-5-5"
    ) match
      case Right(QueueCmd.Add(_, _, _, _, by, _)) =>
        assertEquals(
          by.createdBy,
          Some(
            Creation.By(
              Some(Creation.Plugin("research-workspace")),
              Some(Creation.Workflow("review-article")),
              Some(Creation.Definition("article-skeptic")),
              Some(Creation.Provider("anthropic")),
              Some(Creation.Model("claude-opus-5-5"))
            )
          )
        )
      case other => fail(s"${other.toString}")
    parse("tasks", "add", "enrich x", "--by-plugin", "p") match
      case Right(QueueCmd.Add(_, _, _, _, by, _)) =>
        assertEquals(by, AgentIdentity(plugin = Some(Creation.Plugin("p"))))
      case other => fail(s"${other.toString}")

  test("`queue patch <id> --actor` hands an item to the agents or takes it back"):
    assertEquals(
      parse("patch", "7", "--actor", "agents"),
      Right(QueueCmd.Patch(Queue.Id(7), Some(Creation.Actor("agents")), json))
    )

  test("`add` without text is a usage error, not an empty item"):
    assert(parse("acquire", "add").isLeft)
    assert(parse("acquire", "add", "  ").isLeft)

  test("done / rm take an integer id; a word is a malformed id"):
    assertEquals(parse("done", "12"), Right(QueueCmd.Done(Queue.Id(12), json)))
    assertEquals(parse("rm", "3"), Right(QueueCmd.Rm(Queue.Id(3), json)))
    parse("done", "twelve") match
      case Left(CommandLine.Outcome.Failed(_: Problem.Invalid.Malformed, _, _)) => ()
      case other                                                                => fail(s"${other.toString}")

  test("edit takes an id and the new text"):
    assertEquals(
      parse("edit", "12", "review-article x: 1 open MAJOR — R1: y"),
      Right(QueueCmd.Edit(Queue.Id(12), QueueItem.Text("review-article x: 1 open MAJOR — R1: y"), json))
    )
    assert(parse("edit", "12").isLeft)
    assert(parse("edit", "12", "  ").isLeft)
    assert(parse("edit", "x", "t").isLeft)

  test("export / import take a directory"):
    assertEquals(parse("export", "/tmp/meta"), Right(QueueCmd.Export("/tmp/meta")))
    assertEquals(parse("import", "/tmp/meta"), Right(QueueCmd.Import("/tmp/meta")))

  test("an unknown list or verb, or none, is refused"):
    assert(parse("notes").isLeft)
    assert(parse().isLeft)
    assert(parse("acquire", "wat", "x").isLeft)

  test("`queue acquire recheck` takes --dry-run, --limit and --id; only the acquisition list has it"):
    assertEquals(
      parse("acquire", "recheck"),
      Right(QueueCmd.Recheck(Apply, None, None, all = false, Follow, json))
    )
    assertEquals(
      parse("acquire", "recheck", "--dry-run", "--limit", "5"),
      Right(QueueCmd.Recheck(DryRun, Some(5), None, all = false, Follow, json))
    )
    assertEquals(
      parse("acquire", "recheck", "--id", "42"),
      Right(QueueCmd.Recheck(Apply, None, Some(42), all = false, Follow, json))
    )
    assert(parse("acquire", "recheck", "--limit", "-1").isLeft)
    assert(parse("acquire", "recheck", "--id", "x").isLeft)
    assert(parse("acquire", "--dry-run").isLeft, "the flags belong to recheck")
    assert(parse("done", "3", "--id", "3").isLeft)
    assert(parse("tasks", "recheck").isLeft, "only the acquisition list is re-checked")

  test("`queue acquire recheck --all`: every open item; --no-wait returns with the job id; not with --id or --limit"):
    assertEquals(
      parse("acquire", "recheck", "--all"),
      Right(QueueCmd.Recheck(Apply, None, None, all = true, Follow, json))
    )
    assertEquals(
      parse("acquire", "recheck", "--all", "--no-wait"),
      Right(QueueCmd.Recheck(Apply, None, None, all = true, Return, json))
    )
    assertEquals(
      parse("acquire", "recheck", "--all", "--dry-run"),
      Right(QueueCmd.Recheck(DryRun, None, None, all = true, Follow, json)),
      "a dry run of --all lists every item it would try"
    )
    assertEquals(
      parse("acquire", "recheck", "--limit", "20", "--no-wait", "--json"),
      Right(QueueCmd.Recheck(Apply, Some(20), None, all = false, Return, Cli.JsonOutput.Json))
    )
    assert(parse("acquire", "recheck", "--all", "--id", "4").isLeft)
    assert(parse("acquire", "recheck", "--all", "--limit", "4").isLeft)
    assert(parse("tasks", "--no-wait").isLeft)
    assertEquals(
      parse("acquire", "--all"),
      Right(QueueCmd.Show(Docket.Label("acquire"), true, json)),
      "--all without recheck still shows the done items"
    )

  test("the help names all three lists and every verb"):
    val help = Parsing.help("queue", "--help")
    List("acquire", "tasks", "log", "done", "edit", "rm", "export", "import")
      .foreach(w => assert(help.contains(w), s"the help does not mention $w"))
    assert(Parsing.help("queue", "acquire", "--help").contains("recheck"))
    assert(Parsing.help("queue", "acquire", "--help").contains("add"))
