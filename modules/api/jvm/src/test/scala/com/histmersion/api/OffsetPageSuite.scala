package com.histmersion.api


/** [[OffsetPage]] (#1798 item 8): one page shape — total, offset, next — and one Markdown header and
  * footer for every offset-paged list, so a list that is cut says how to get the rest. */
class OffsetPageSuite extends munit.FunSuite:

  test("next: the offset after this page, none on the last page or past the end"):
    assertEquals(OffsetPage(412, 0, 50).next, Some(50))
    assertEquals(OffsetPage(412, 400, 12).next, None)
    assertEquals(OffsetPage(412, 500, 0).next, None)
    assertEquals(OffsetPage(0, 0, 0).next, None)

  test("header: the whole list, or how much of it"):
    assertEquals(OffsetPage.header(OffsetPage(7, 0, 7), "event(s)"), "### 7 event(s)")
    assertEquals(OffsetPage.header(OffsetPage(412, 0, 50), "event(s)"), "### 50 of 412 event(s)")
    assertEquals(OffsetPage.header(OffsetPage(412, 400, 12), "event(s)"), "### 12 of 412 event(s)")

  test("footer: empty for the whole list; the range and the next offset otherwise"):
    assertEquals(OffsetPage.footer(OffsetPage(7, 0, 7)), "")
    assertEquals(OffsetPage.footer(OffsetPage(412, 50, 50)), "\n\n_showing 51–100 of 412 — next: offset=100 — full set: limit=412_")
    assertEquals(OffsetPage.footer(OffsetPage(412, 400, 12)), "\n\n_showing 401–412 of 412 — full set: limit=412_")
    assert(!OffsetPage.footer(OffsetPage(9000, 0, 50)).contains("full set"))

  test("markdown: the empty text, and past the end says so"):
    assertEquals(OffsetPage.markdown(OffsetPage(0, 0, 0), "goal(s)", "_No goals._", Nil)((_: Int).toString), "_No goals._")
    assert(OffsetPage.markdown(OffsetPage(3, 10, 0), "goal(s)", "_No goals._", Nil)((_: Int).toString).contains("past the end"))

  private val g = Goal("g1", "A goal", "planned")
  private def ev(i: Int) = EventSummary(s"e$i", s"Event $i", "battle", TimeSpan(), "attested")

  test("every paged list prints its footer: events, goals, attestations, asset links, pictures (#1798)"):
    val events = Markdown.eventList(EventList.of(412, List(ev(1), ev(2)), 50))
    assert(events.startsWith("### 2 of 412 event(s)") && events.contains("next: offset=52"), events)
    val goals = Markdown.goalList(GoalList.of(30, List(g), 0))
    assert(goals.contains("### 1 of 30 goal(s)") && goals.contains("next: offset=1"), goals)
    val all = Markdown.goalList(GoalList.of(1, List(g), 0))
    assert(all.startsWith("### 1 goal(s)") && !all.contains("showing"), all)
    val att = Markdown.attestationList(AttestationList.of(5, Nil, 10))
    assert(att.contains("past the end"), att)
    val links = Markdown.assetLinkList(AssetLinkList.of(0, Nil, 0))
    assertEquals(links, "_No asset links._")

  test("the JSON carries offset and next; a body without them still decodes (older server)"):
    import io.circe.syntax.*
    val j = EventList.of(412, List(ev(1)), 50).asJson
    assertEquals(j.hcursor.get[Int]("offset"), Right(50))
    assertEquals(j.hcursor.get[Option[Int]]("next"), Right(Some(51)))
    val old = io.circe.parser.decode[GoalList]("""{"total":1,"goals":[]}""")
    assertEquals(old.map(_.offset), Right(0))
