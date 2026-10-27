package com.histmersion.cli

import com.histmersion.cli.Ops.{QueueArgs, QueueCmd}

/** `histmersion queue …` keeps the grammar the plan specifies rather than decline's subcommand-per-verb
  * shape, so the positional dispatch is a pure function — and therefore has tests. */
class OpsArgsSuite extends munit.FunSuite:

  private def parse(args: String*) = QueueArgs.parse(args.toList, None, None, all = false)

  test("`queue <list>` shows that list"):
    assertEquals(parse("acquire"), Right(QueueCmd.Show("acquire", false)))
    assertEquals(parse("tasks"), Right(QueueCmd.Show("tasks", false)))
    assertEquals(parse("log"), Right(QueueCmd.Show("log", false)))

  test("--all is carried into the show command"):
    assertEquals(QueueArgs.parse(List("tasks"), None, None, all = true), Right(QueueCmd.Show("tasks", true)))

  test("`queue <list> add \"text\"` adds, with --topic when given"):
    assertEquals(parse("acquire", "add", "Eller, Myth of Matriarchal Prehistory"),
      Right(QueueCmd.Add("acquire", "Eller, Myth of Matriarchal Prehistory", None, None)))
    assertEquals(QueueArgs.parse(List("tasks", "add", "enrich x"), Some("Backlog"), None, all = false),
      Right(QueueCmd.Add("tasks", "enrich x", Some("Backlog"), None)))

  test("`add` without text is a usage error, not an empty item"):
    assert(parse("acquire", "add").isLeft)
    assert(parse("acquire", "add").left.exists(_.contains("needs the item text")))

  test("done / rm take an integer id"):
    assertEquals(parse("done", "12"), Right(QueueCmd.Done(12)))
    assertEquals(parse("rm", "3"), Right(QueueCmd.Rm(3)))
    assert(parse("done", "twelve").left.exists(_.contains("is not an item id")))

  test("edit takes an id and the new text (the article's one review item is rewritten, #934)"):
    assertEquals(parse("edit", "12", "review-article x: 1 open MAJOR — R1: y"), Right(QueueCmd.Edit(12, "review-article x: 1 open MAJOR — R1: y")))
    assert(parse("edit", "12").left.exists(_.contains("takes an item id and the new text")))
    assert(parse("edit", "12", "  ").left.exists(_.contains("needs the new text")))
    assert(parse("edit", "x", "t").left.exists(_.contains("is not an item id")))

  test("export / import take a directory"):
    assertEquals(parse("export", "/tmp/meta"), Right(QueueCmd.Export("/tmp/meta")))
    assertEquals(parse("import", "/tmp/meta"), Right(QueueCmd.Import("/tmp/meta")))

  test("an unknown list or verb reports the usage line"):
    assert(parse("notes").left.exists(_.contains("unknown list or verb 'notes'")))
    assertEquals(parse(), Left(QueueArgs.usage))
    assertEquals(parse("acquire", "wat", "x"), Left(QueueArgs.usage))

  test("`queue acquire recheck` takes --dry-run, --limit and --id; the flags belong to it alone"):
    assertEquals(parse("acquire", "recheck"), Right(QueueCmd.Recheck(false, None, None)))
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = false, dryRun = true, limit = Some(5)),
      Right(QueueCmd.Recheck(true, Some(5), None)))
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = false, item = Some(42)),
      Right(QueueCmd.Recheck(false, None, Some(42))))
    assert(QueueArgs.parse(List("acquire", "recheck"), None, None, all = false, limit = Some(-1)).left.exists(_.contains("--limit")))
    assert(QueueArgs.parse(List("acquire"), None, None, all = false, dryRun = true).left.exists(_.contains("go with `queue acquire recheck`")))
    assert(QueueArgs.parse(List("done", "3"), None, None, all = false, item = Some(3)).isLeft)
    assertEquals(parse("tasks", "recheck"), Left(QueueArgs.usage), "only the acquisition list is re-checked")

  test("the decline options parse into the recheck command"):
    val cmd = com.monovore.decline.Command("histmersion", "test")(Ops.queue)
    assert(cmd.parse(List("queue", "acquire", "recheck", "--dry-run", "--limit", "5", "--json"), Map.empty).isRight)
    assert(cmd.parse(List("queue", "acquire", "recheck", "--id", "42"), Map.empty).isRight)
    assert(cmd.parse(List("queue", "acquire", "recheck", "--id", "x"), Map.empty).isLeft)

  test("`queue acquire recheck --all`: every open item; --no-wait returns with the job id; neither goes elsewhere"):
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = true),
      Right(QueueCmd.Recheck(false, None, None, all = true)))
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = true, noWait = true),
      Right(QueueCmd.Recheck(false, None, None, all = true, noWait = true)))
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = true, dryRun = true),
      Right(QueueCmd.Recheck(true, None, None, all = true)), "a dry run of --all lists every item it would try")
    assertEquals(QueueArgs.parse(List("acquire", "recheck"), None, None, all = false, limit = Some(20), noWait = true),
      Right(QueueCmd.Recheck(false, Some(20), None, noWait = true)))
    assert(QueueArgs.parse(List("acquire", "recheck"), None, None, all = true, item = Some(4)).left.exists(_.contains("not both")))
    assert(QueueArgs.parse(List("acquire", "recheck"), None, None, all = true, limit = Some(4)).left.exists(_.contains("not both")))
    assert(QueueArgs.parse(List("tasks"), None, None, all = false, noWait = true).left.exists(_.contains("--no-wait")))
    assertEquals(QueueArgs.parse(List("acquire"), None, None, all = true), Right(QueueCmd.Show("acquire", true)),
      "--all without recheck still shows the done items")
    val cmd = com.monovore.decline.Command("histmersion", "test")(Ops.queue)
    assert(cmd.parse(List("queue", "acquire", "recheck", "--all", "--no-wait"), Map.empty).isRight)
    assert(cmd.parse(List("queue", "acquire", "recheck", "--all", "--json"), Map.empty).isRight)
    assert(QueueArgs.usage.contains("--all") && QueueArgs.usage.contains("--no-wait"))

  test("the usage line names all three lists and every verb"):
    List("acquire", "tasks", "log", "add", "done", "rm", "export", "import", "recheck")
      .foreach(w => assert(QueueArgs.usage.contains(w), s"usage does not mention $w"))
