package com.histmersion.api

import com.histmersion.api.automation.GoalsMarkdown
import com.histmersion.api.catalogue.EventsMarkdown
import com.histmersion.api.catalogue.AttestationsMarkdown
import com.histmersion.api.media.MediaMarkdown
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{EventList, EventSummary}
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.media.AssetLink

/** [[OffsetPage]] (#1798 item 8): one page shape — total, offset, next — and one Markdown header and footer for every
  * offset-paged list, so a list that is cut says how to get the rest.
  */
class OffsetPageSuite extends munit.FunSuite:

  test("next: the offset after this page, none on the last page or past the end"):
    assertEquals(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(50)).next.map(n => (n.value: Int)), Some(50))
    assertEquals(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(400), com.histmersion.api.RowCount(12)).next, None)
    assertEquals(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(500), com.histmersion.api.RowCount(0)).next, None)
    assertEquals(OffsetPage(com.histmersion.api.RowCount(0), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(0)).next, None)

  test("header: the whole list, or how much of it"):
    assertEquals(OffsetPage.header(OffsetPage(com.histmersion.api.RowCount(7), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(7)), "event(s)"), "### 7 event(s)")
    assertEquals(OffsetPage.header(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(50)), "event(s)"), "### 50 of 412 event(s)")
    assertEquals(OffsetPage.header(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(400), com.histmersion.api.RowCount(12)), "event(s)"), "### 12 of 412 event(s)")

  test("footer: empty for the whole list; the range and the next offset otherwise"):
    assertEquals(OffsetPage.footer(OffsetPage(com.histmersion.api.RowCount(7), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(7))), "")
    assertEquals(
      OffsetPage.footer(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(50), com.histmersion.api.RowCount(50))),
      "\n\n_showing 51–100 of 412 — next: offset=100 — full set: limit=412_"
    )
    assertEquals(OffsetPage.footer(OffsetPage(com.histmersion.api.RowCount(412), com.histmersion.api.Offset(400), com.histmersion.api.RowCount(12))), "\n\n_showing 401–412 of 412 — full set: limit=412_")
    assert(!OffsetPage.footer(OffsetPage(com.histmersion.api.RowCount(9000), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(50))).contains("full set"))

  test("markdown: the empty text, and past the end says so"):
    assertEquals(
      OffsetPage.markdown(OffsetPage(com.histmersion.api.RowCount(0), com.histmersion.api.Offset(0), com.histmersion.api.RowCount(0)), "goal(s)", "_No goals._", Nil)((_: Int).toString),
      "_No goals._"
    )
    assert(
      OffsetPage
        .markdown(OffsetPage(com.histmersion.api.RowCount(3), com.histmersion.api.Offset(10), com.histmersion.api.RowCount(0)), "goal(s)", "_No goals._", Nil)((_: Int).toString)
        .contains("past the end")
    )

  private val g = Goal(com.histmersion.api.automation.Goal.Slug("g1"), com.histmersion.api.automation.Goal.Title("A goal"), com.histmersion.api.automation.Goal.StatusValue("planned"))
  private def ev(i: Int) = EventSummary(s"e$i", s"Event $i", com.histmersion.api.catalogue.EventSummary.Kind("battle"), TimeSpan(), com.histmersion.api.catalogue.EventSummary.Historicity("attested"))

  test("every paged list prints its footer: events, goals, attestations, asset links, pictures (#1798)"):
    val events = EventsMarkdown.eventList(EventList.of(com.histmersion.api.catalogue.EventList.Total(412), List(ev(1), ev(2)), com.histmersion.api.personal.UnreadPage.Offset(50)))
    assert(events.startsWith("### 2 of 412 event(s)") && events.contains("next: offset=52"), events)
    val goals = GoalsMarkdown.goalList(Goal.List.of(com.histmersion.api.RowCount(30), List(g), com.histmersion.api.personal.UnreadPage.Offset(0)))
    assert(goals.contains("### 1 of 30 goal(s)") && goals.contains("next: offset=1"), goals)
    val all = GoalsMarkdown.goalList(Goal.List.of(com.histmersion.api.RowCount(1), List(g), com.histmersion.api.personal.UnreadPage.Offset(0)))
    assert(all.startsWith("### 1 goal(s)") && !all.contains("showing"), all)
    val att = AttestationsMarkdown.attestationList(Attestation.List.of(com.histmersion.api.RowCount(5), Nil, com.histmersion.api.personal.UnreadPage.Offset(10)))
    assert(att.contains("past the end"), att)
    val links = MediaMarkdown.assetLinkList(AssetLink.List.of(com.histmersion.api.RowCount(0), Nil, com.histmersion.api.personal.UnreadPage.Offset(0)))
    assertEquals(links, "_No asset links._")

  test("the JSON carries offset and next; a body without them still decodes (older server)"):
    import io.circe.syntax.*
    val j = EventList.of(com.histmersion.api.catalogue.EventList.Total(412), List(ev(1)), com.histmersion.api.personal.UnreadPage.Offset(50)).asJson
    assertEquals(j.hcursor.get[Int]("offset"), Right(50))
    assertEquals(j.hcursor.get[Option[Int]]("next"), Right(Some(51)))
    val old = io.circe.parser.decode[Goal.List]("""{"total":1,"goals":[]}""")
    assertEquals(old.map(x => (x.offset.value: Int)), Right(0))
