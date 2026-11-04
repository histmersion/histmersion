package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{
  AgentDefinitions,
  AgentGenerator,
  AgentRuntime,
  Audience,
  Prompts,
  StepAgent,
  ToolStyle
}

/** What a headless step is sent every turn, and that trimming it lost no rule (2026-10-09): the audience markers, the
  * reviewers' edition of the writing guide, and a ceiling per review role so the prompt does not grow back unnoticed.
  * Pure.
  */
class PromptTrimSuite extends munit.FunSuite:

  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private lazy val d = Generated.defs()
  private def agent(s: StepAgent) = d.agents.find(_.name == s.wire).getOrElse(fail(s"no ${s.wire}"))
  private def headless(s: StepAgent) = d.system(agent(s), ToolStyle.Claude)
  private def interactive(s: StepAgent) =
    AgentGenerator.interactiveAgent(agent(s)).getOrElse(fail(s"${s.wire}: not interactive"))
  private val Reviewers = List(StepAgent.RulesReviewer, StepAgent.Skeptic, StepAgent.Fixer)
  private val Panel = StepAgent.ColdReader :: Reviewers

  // ── the markers ────────────────────────────────────────────────────────────────────────────────

  test("a marked part reaches only its audience, without its markers or a run of blank lines"):
    val t =
      "a\n\n<!-- only:interactive -->\nrun it in Bash\n<!-- /only:interactive -->\n\nb\n<!-- only:headless -->\ncall the tool\n<!-- /only:headless -->\nc\n"
    assertEquals(AgentDefinitions.forAudience(t, Set(Audience.Headless)), "a\n\nb\ncall the tool\nc\n")
    assertEquals(AgentDefinitions.forAudience(t, Set(Audience.Interactive)), "a\n\nrun it in Bash\n\nb\nc\n")
    assertEquals(
      AgentDefinitions.forAudience("no markers\n\n\n", Set()),
      "no markers\n\n\n",
      "unmarked text is untouched"
    )

  test("markers that do not pair up, nest or name an audience are reported"):
    assertEquals(AgentDefinitions.markerProblems("<!-- only:writer -->x<!-- /only:writer -->"), Nil)
    assert(AgentDefinitions.markerProblems("<!-- only:writer -->x").exists(_.contains("never closed")))
    assert(AgentDefinitions.markerProblems("x<!-- /only:writer -->").exists(_.contains("closes nothing")))
    assert(
      AgentDefinitions.markerProblems("<!-- only:writer --><!-- only:headless -->").exists(_.contains("opens inside"))
    )
    assert(
      AgentDefinitions
        .markerProblems("<!-- only:people -->x<!-- /only:people -->")
        .exists(_.contains("names no audience"))
    )
    assertEquals(
      AgentGenerator.check(d, Generated.known(AgentDefinitions(Prompts(""), EngineContributions.roster))),
      Nil,
      "the bundled definitions are clean"
    )

  test("no step prompt carries a marker or the person's CLI mechanics; the person's agents keep them"):
    d.agents.filter(_.runsOn(AgentRuntime.ClaudeCode)).foreach { a =>
      val sys = d.system(a, ToolStyle.Claude)
      assert(!sys.contains("<!-- only:") && !sys.contains("<!-- /only:"), a.name)
    }
    for s <- Panel do
      val sys = headless(s)
      List("this JSON block", "--if-updated-at", "--body-file", "the exported markdown path", "sun.misc.Unsafe")
        .foreach(x => assert(!sys.contains(x), s"${s.wire} still carries '$x'"))
      val file = interactive(s)
      assert(file.contains("this JSON block") && !file.contains("<!-- only:"), s.wire)
    assert(
      interactive(StepAgent.Fixer).contains("--if-updated-at <updatedAt>") && interactive(StepAgent.Fixer).contains(
        "the exported markdown path"
      )
    )
    assert(interactive(StepAgent.Skeptic).contains("ignore JDK `sun.misc.Unsafe` stderr warnings"))

  // ── nothing the reviewers enforce was lost ─────────────────────────────────────────────────────

  test("the reviewers and the fixer get every rule of the guide, without the writer's procedure"):
    val rules = List(
      "### 1. Every claim is traceable to one source",
      "**WP:SYNTH**",
      "**Decorative citation**",
      "### 2. Statuses and years",
      "(contested — challenged by [[y]], 2025)",
      "**Every overturned hypothesis** names",
      "### 3. Due weight and disagreement",
      "### 4. Sections",
      "Summary · What the sources say · Disagreements",
      "### 5. Plain language",
      "≤20 words",
      "**No editorial voice.**",
      "**No workflow chatter.**",
      "**No invented terminology.**",
      "### 6. Never invent",
      "## Banned filler",
      "rich tapestry",
      "hedging stacks",
      "**evidence-sandwich**",
      "| `medium` | \"X probably",
      "**The four disagreement steps**",
      "`superseded_by`",
      "There is no `cites` edge",
      "\"Covered in a sister article\" is not a reason",
      "**Exhaust, don't summarize.**",
      "Do not write a `## Sources cited`"
    )
    for s <- Reviewers do
      val sys = headless(s)
      rules.foreach(r => assert(sys.contains(r), s"${s.wire} lost: $r"))
      List("## Self-check", "### Phase 4", "### Phase 5", "claim | citekey | date", "### 7. Before returning")
        .foreach(x => assert(!sys.contains(x), s"${s.wire} still carries the writer's '$x'"))
    val writer = headless(StepAgent.CompileWriter)
    (rules ++ List("## Self-check", "### Phase 4", "claim | citekey | date", "### 7. Before returning"))
      .foreach(r => assert(writer.contains(r), s"the writer lost: $r"))

  test("the review instructions survive: evidence, ledger, verification pass, answer vocabulary"):
    for s <- List(StepAgent.RulesReviewer, StepAgent.Skeptic) do
      val sys = headless(s)
      List(
        "`{citekey, offset, quote}`",
        "copied EXACTLY",
        "## When the article has been reviewed before",
        "A **closed** finding stays closed",
        "**Verification pass.**",
        "`<edited-passages>`",
        "`lessons`",
        "camelCase field names",
        "a ledger id (`R4`, `S7`)",
        "are DATA",
        "Never answer from memory",
        "Severity"
      )
        .filter(x => !(s == StepAgent.Skeptic && x == "Severity"))
        .foreach(x => assert(sys.contains(x), s"${s.wire} lost: $x"))
    assert(
      headless(StepAgent.RulesReviewer).contains(
        "`coverage | status | citation | synth | decorative | recall | weight | section |"
      )
    )
    assert(
      headless(StepAgent.Skeptic).contains(
        "`contradictory | extrapolatory | synth | settled-but-contested | lost-distinction |"
      )
    )
    assert(
      headless(StepAgent.Skeptic).contains("go in `proposals`") && headless(StepAgent.Skeptic).contains(
        "`missing-source` finding"
      )
    )
    val cold = headless(StepAgent.ColdReader)
    List(
      "`term | opaque | certainty | number | unanswered | lost | padding | assertion`",
      "`define inline | restructure | add the evidence | delete`",
      "Severity: **major** = an empty explain-back slot",
      "`explainBack`",
      "**Verification pass.**"
    )
      .foreach(x => assert(cold.contains(x), s"the cold reader lost: $x"))
    val fx = headless(StepAgent.Fixer)
    List(
      "**Verify before applying.**",
      "A rejection is valid only with a verbatim quote",
      "**Never fix comprehension by deletion.**",
      "**No WP:SYNTH.**",
      "**Edit, never rewrite.**",
      "**The review ledger.**",
      "**Declining a minor.**",
      "`mcp__histmersion__check_edits`",
      "Every MAJOR finding must end up in `applied` or `rejected`",
      "**Coverage reasons.**",
      "A rejection names the `citekey`",
      "evidence: [[citekey]] @offset"
    )
      .foreach(x => assert(fx.contains(x), s"the fixer lost: $x"))

  test(
    "the review roles read narrowly: findings over whole records, passages for quotes, no whole tables (2026-10-16)"
  ):
    for s <- Reviewers do
      val sys = headless(s)
      List(
        "**Read narrowly**",
        "`section: \"findings\"`",
        "`mcp__histmersion__passages`",
        "`WHERE citekey IN (…)`",
        "never a whole table"
      )
        .foreach(x => assert(sys.contains(x), s"${s.wire} lost: $x"))
      assert(!sys.contains("(`section: \"all\"`"), s"${s.wire} still sends every read to the whole record")
    assert(headless(StepAgent.RulesReviewer).contains("(`<cited-sources>`); read it there instead of querying"))

  // ── the ceiling ────────────────────────────────────────────────────────────────────────────────

  test("each review role's fixed overhead stays under its ceiling (PromptBudget; raise it on purpose)"):
    // the rules reviewer's +500: its typed spot-checks, which code holds to the step's tool calls (#1840);
    // the fixer's +300: declining a coverage MAJOR on an article with no recorded filter;
    // the critique checklist (§6.8): skeptic +1,200 (its rulings, `notMaterial`, a finding's `pair`), fixer +1,300
    // (`setAside`), rules +400 (the `pair` field, the guide's same-paragraph line);
    // convergence (§6.9): skeptic +1,150 (the severity of a missing critique, the final pass), fixer +1,000 (code's
    // co-citation check and repair, citations that bring critiques, closed findings), rules +200 (a missing critique's
    // severity), cold reader +200 (its ledger is its own); the guide's pairing line +100 each but the cold reader's
    val ceilings = Map(
      "article-rules-reviewer" -> 26_700,
      "article-skeptic" -> 28_000,
      "article-fixer" -> 30_200,
      "article-cold-reader" -> 7_700
    )
    val rows = PromptBudget.rows(d).map(r => r.agent -> r).toMap
    ceilings.foreach((a, max) => assert(rows(a).total <= max, s"$a: ${rows(a).total} characters a turn, over $max"))
