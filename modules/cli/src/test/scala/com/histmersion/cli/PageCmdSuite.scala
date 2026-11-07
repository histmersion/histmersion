package com.histmersion.cli

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Paging

/** The "next page" command a paged listing prints: the same options, plus `--before`. */
class PageCmdSuite extends munit.FunSuite:

  test("the next page repeats the options given, in order, and adds the cursor"):
    assertEquals(
      PageCmd.next("jobs", List("status" -> Some("failed"), "limit" -> Some("20")), com.histmersion.api.Paging.Cursor("4f1c-9a")),
      "histmersion jobs --status failed --limit 20 --before 4f1c-9a"
    )
    assertEquals(
      PageCmd.next("agent runs", List("status" -> None, "limit" -> None, "goal" -> Some("hittites")), com.histmersion.api.Paging.Cursor("r1")),
      "histmersion agent runs --goal hittites --before r1"
    )
    assertEquals(PageCmd.next("agent approvals", Nil, com.histmersion.api.Paging.Cursor("12")), "histmersion agent approvals --before 12")

  test("a value the shell would split is single-quoted"):
    assertEquals(
      PageCmd.next("agent runs", List("goal" -> Some("bronze age")), com.histmersion.api.Paging.Cursor("r1")),
      "histmersion agent runs --goal 'bronze age' --before r1"
    )
    assertEquals(
      PageCmd.next("jobs", List("status" -> Some("it's")), com.histmersion.api.Paging.Cursor("j")),
      "histmersion jobs --status 'it'\\''s' --before j"
    )

  test("the older line appears only when there is an older page"):
    val more = Paging.Page(com.histmersion.api.Paging.Page.Total(40), Some(com.histmersion.api.Paging.Cursor("j20")), Nil)
    assertEquals(
      PageCmd.older(more, "jobs", List("limit" -> Some("20"))),
      Some("Older: `histmersion jobs --limit 20 --before j20`")
    )
    assertEquals(PageCmd.older(more.modify(_.next).setTo(None), "jobs", Nil), None)
