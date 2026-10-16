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

  test("the usage line names all three lists and every verb"):
    List("acquire", "tasks", "log", "add", "done", "rm", "export", "import")
      .foreach(w => assert(QueueArgs.usage.contains(w), s"usage does not mention $w"))
