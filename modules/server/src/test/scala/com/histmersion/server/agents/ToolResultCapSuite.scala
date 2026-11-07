package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ToolResultCap

import com.histmersion.api.library.Query.QueryResult

/** What one agent tool call may return (`agents.tools`): a cut is at a line and says how to read on, a source record
  * keeps its findings whole by shortening its notes first, UI chip links lose their targets, a query shows few rows.
  * Pure.
  */
class ToolResultCapSuite extends munit.FunSuite:

  test("a result within the cap is untouched; a longer one is cut at a line and says so"):
    assertEquals(ToolResultCap.cap("short", com.histmersion.domain.automation.agents.ToolResultCap.CapMax(100), "x"), "short")
    assertEquals(ToolResultCap.cap("anything", com.histmersion.domain.automation.agents.ToolResultCap.CapMax(0), "x"), "anything", "0 = no cap")
    val text = (1 to 400).map(i => s"line $i of the result").mkString("\n")
    val cut = ToolResultCap.cap(text, com.histmersion.domain.automation.agents.ToolResultCap.CapMax(2000), "ask for less")
    assert(cut.length <= 2000, cut.length)
    assert(cut.endsWith("characters — ask for less_"), cut.takeRight(120))
    assert(cut.contains(s"of ${text.length} characters"), cut.takeRight(120))
    val kept = cut.substring(0, cut.indexOf("\n\n_…cut at"))
    assert(text.startsWith(kept) && kept.endsWith("of the result"), "cut at a line break: " + kept.takeRight(40))

  test("a line longer than the cap is cut mid-line rather than shown empty"):
    val cut = ToolResultCap.cap("x" * 5000, com.histmersion.domain.automation.agents.ToolResultCap.CapMax(1000), "h")
    assert(cut.startsWith("x" * 500) && cut.length <= 1000, cut.length)

  test("chip links lose their targets; article links and wikilinks stay"):
    val md = "**Topics:** [pie religion](/#topic=pie_religion), [oral tradition](/#topic=oral_tradition)  \n" +
      "- [Dragon-Slayer](/article/dragon_slayer) cites [[watkins1995_dragon]]; see [a figure](http://h/x.png)"
    assertEquals(
      ToolResultCap.plainLinks(md),
      "**Topics:** pie religion, oral tradition  \n- [Dragon-Slayer](/article/dragon_slayer) cites [[watkins1995_dragon]]; see [a figure](http://h/x.png)"
    )

  private val Record =
    "# How to Kill a Dragon\n\n_[Calvert Watkins](/#author=Calvert%20Watkins) · 1995 · foundational_\n\n" +
      "**Topics:** [pie religion](/#topic=pie_religion)  \n**Regions:** [Anatolia](/#place=Anatolia)  \n" +
      "**Provenance:** archive_org  \n**Uid:** 01a102ee-d28a-75e8-9beb-7c8ee0d08957\n\nThe summary.\n\n" +
      "## Notes\n\n" + (1 to 300).map(i => s"Note line $i about the book.").mkString("\n") +
      "\n\n## Findings (2)\n- The formula *gʷhen- dhgʷhim recurs.\n- It is Proto-Indo-European." +
      "\n\n## Mentioned in articles (1)\n- [Dragon-Slayer](/article/dragon_slayer)"

  test("a source record drops its identity lines and chip links"):
    val c = ToolResultCap.compactSource(Record)
    assert(!c.contains("Uid:") && !c.contains("Provenance:") && !c.contains("/#topic"), c.take(400))
    assert(
      c.contains("_Calvert Watkins · 1995 · foundational_") && c.contains("**Regions:** Anatolia\n\nThe summary."),
      c.take(400)
    )

  test("a record over the cap shortens its notes first: summary and findings arrive whole"):
    val c = ToolResultCap.compactSource(Record)
    val r = ToolResultCap.source(c, com.histmersion.domain.automation.agents.ToolResultCap.SourceMax(4000))
    assert(r.length <= 4000, r.length)
    assert(
      r.contains("The summary.") && r.contains(
        "## Findings (2)\n- The formula *gʷhen- dhgʷhim recurs.\n- It is Proto-Indo-European."
      ),
      r
    )
    assert(r.contains("## Mentioned in articles (1)"), "room left: the mentions stay")
    assert(r.contains("get_source section=\"notes\""), "the notes say how to read them whole")
    assert(r.contains("Note line 1 about") && !r.contains("Note line 300 about"), "the notes are cut")
    assertEquals(ToolResultCap.source(c, c.length), c, "within the cap: untouched")

  test("with no room for the notes they are left out, then the mentions, then the rest is cut"):
    val c = ToolResultCap.compactSource(Record)
    val tight = ToolResultCap.source(c, com.histmersion.domain.automation.agents.ToolResultCap.SourceMax(700))
    assert(tight.length <= 700, tight.length)
    assert(tight.contains("characters of notes left out") || tight.contains("…cut at"), tight)
    val noNotes = ToolResultCap.source(c.replace("## Notes", "## Elsewhere"), com.histmersion.domain.automation.agents.ToolResultCap.SourceMax(300))
    assert(noNotes.length <= 300 && noNotes.contains("section=\"findings\""), noNotes)

  test("a query shows at most its row cap, marked, with how to ask for less"):
    val r = QueryResult(
      List(com.histmersion.api.library.Query.QueryResult.Columns("citekey"), com.histmersion.api.library.Query.QueryResult.Columns("status")),
      (1 to 120).toList.map(i => List(Some(s"ck$i"), Some(com.histmersion.api.library.Query.QueryResult.Rows("current")))),
      com.histmersion.api.library.Query.QueryResult.RowCount(120),
      com.histmersion.api.library.Query.QueryResult.Truncated(false),
      com.histmersion.api.library.Query.QueryResult.ElapsedMs(3)
    )
    val md = com.histmersion.domain.library.workflows.LibraryAgentTools.queryResult(r, com.histmersion.config.AgentToolsConfig.QueryMaxRows(50), com.histmersion.domain.automation.agents.ToolResultCap.CapMax(100000))
    assert(md.startsWith("### 50 row(s) (truncated by the row cap)"), md.take(80))
    assert(md.contains("| ck50 |") && !md.contains("| ck51 |"), "50 rows")
    assert(md.contains("70 more rows not shown") && md.contains("never a whole table"), md.takeRight(200))
    val capped = QueryResult(List(com.histmersion.api.library.Query.QueryResult.Columns("c")), (1 to 500).toList.map(i => List(Some(s"r$i"))), com.histmersion.api.library.Query.QueryResult.RowCount(500), com.histmersion.api.library.Query.QueryResult.Truncated(true), com.histmersion.api.library.Query.QueryResult.ElapsedMs(3))
    assert(
      com.histmersion.domain.library.workflows.LibraryAgentTools
        .queryResult(capped, com.histmersion.config.AgentToolsConfig.QueryMaxRows(50), com.histmersion.domain.automation.agents.ToolResultCap.CapMax(100000))
        .contains("450+ more rows"),
      "the server's row cap hid more"
    )
    val few = r.modify(_.rows).setTo(r.rows.take(3)).modify(_.rowCount).setTo(3)
    assertEquals(
      com.histmersion.domain.library.workflows.LibraryAgentTools.queryResult(few, com.histmersion.config.AgentToolsConfig.QueryMaxRows(50), com.histmersion.domain.automation.agents.ToolResultCap.CapMax(100000)),
      com.histmersion.api.library.Query.markdown(few)
    )
    assert(
      com.histmersion.domain.library.workflows.LibraryAgentTools.queryResult(r, com.histmersion.config.AgentToolsConfig.QueryMaxRows(0), com.histmersion.domain.automation.agents.ToolResultCap.CapMax(600)).length <= 600,
      "no row cap: the character cap still holds"
    )
