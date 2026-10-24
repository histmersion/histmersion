package com.histmersion.cli

import com.histmersion.api.Paging

/** The "next page" command a paged listing prints: the same options, plus `--before`. */
class PageCmdSuite extends munit.FunSuite:

  test("the next page repeats the options given, in order, and adds the cursor"):
    assertEquals(PageCmd.next("jobs", List("status" -> Some("failed"), "limit" -> Some("20")), "4f1c-9a"),
      "histmersion jobs --status failed --limit 20 --before 4f1c-9a")
    assertEquals(PageCmd.next("agent runs", List("status" -> None, "limit" -> None, "goal" -> Some("hittites")), "r1"),
      "histmersion agent runs --goal hittites --before r1")
    assertEquals(PageCmd.next("agent approvals", Nil, "12"), "histmersion agent approvals --before 12")

  test("a value the shell would split is single-quoted"):
    assertEquals(PageCmd.next("agent runs", List("goal" -> Some("bronze age")), "r1"),
      "histmersion agent runs --goal 'bronze age' --before r1")
    assertEquals(PageCmd.next("jobs", List("status" -> Some("it's")), "j"), "histmersion jobs --status 'it'\\''s' --before j")

  test("the older line appears only when there is an older page"):
    val more = Paging.Page(40, Some("j20"), Nil)
    assertEquals(PageCmd.older(more, "jobs", List("limit" -> Some("20"))),
      Some("Older: `histmersion jobs --limit 20 --before j20`"))
    assertEquals(PageCmd.older(more.copy(next = None), "jobs", Nil), None)
