# Skills

A **skill** is a written set of instructions that teaches an AI assistant one of Histmersion's
workflows. How to install them: [Using your AI assistant](../ai-assistant.md#3-skills-and-the-claude-code-plugin).

Nineteen skills ship with the repo (`skills/<name>/SKILL.md`) and install into a workspace through
the plugin — see [`plugin/README.md`](https://github.com/histmersion/histmersion/blob/master/plugin/README.md). One canonical copy, which names tools by
placeholder (`{% raw %}{{tool:get_source}}{% endraw %}`); the agent generator renders it per runtime — `plugin/skills/` is
its checked-in Claude Code rendering (`just plugin`; a test fails when it is stale), and
`.claude/skills`, `.opencode/skill` point there. Edit `skills/` and `agents/`, never `plugin/`. Five are
the reference skills for the tool surface:

| skill | use it when |
|---|---|
| `histmersion-query` | answering a research question against the corpus: `facets` → `search` → `get_source` → `neighbors` → `followups`. Carries the MCP-vs-CLI parameter table and three worked recipes (topic + disagreements, place + period, "what has been challenged since YEAR"). |
| `histmersion-ingest` | getting sources in (`ingest_doi`, `ingest`, `seed`, Calibre, archive.org) and enriching them with `patch_source` — findings, topics, regions, period, status, edges. |
| `histmersion-compile` | writing the answer up and filing it as an article with `put_article`/`patch_article`. The rules themselves live in [`docs/writing-guide.md`](https://github.com/histmersion/histmersion/blob/master/docs/writing-guide.md). |
| `histmersion-currency` | keeping statuses honest: refresh the retraction mirror, `assess`, `followups --since`, move sources to `contested`/`superseded`/`debunked`, queue what is missing. |
| `histmersion-dev` | changing Histmersion itself: sbt 2 gotchas, GraalVM native image and its tracing-agent metadata, Flyway migrations, the native bundle. |

The other fourteen are research workflows built on them:

| skill | use it when |
|---|---|
| `trailhead` | starting a topic from a Wikipedia article or a query: harvest the primary literature into an ingest queue |
| `ingest-paper` | one paper by DOI/arXiv/title: fetch, OCR, reference edges, then enrich |
| `import-file` · `import-archive` · `import-calibre` | a file the user downloaded · an archive.org public text · a book already in Calibre |
| `assess-paper` · `find-followups` | vetting a paper (retractions, corrections, impact) · walking the citation graph forward for critiques |
| `update-topic` | refreshing a topic with what was published since its last expansion |
| `report` | answering a research question and filing the answer as a `report` article |
| `review-article` | the three-reviewer panel plus fixer that gates every compiled article |
| `lint-wiki` | corpus health checks and the judgement checks behind them |
| `sync-calibre` · `enrich-calibre` | getting every fetched original into Calibre · auditing and fixing Calibre metadata |
| `bookkeeping` | where bookkeeping goes: the acquire / tasks / log queues |

`./tools/check_skills.sh` verifies that every `histmersion …` command and every MCP tool name the skills
mention actually exists (CLI `--help` prints a Usage block; the name is in the live `tools/list`).
`./tools/gen-cli-docs.sh --check` does the same for the CLI reference table above.
