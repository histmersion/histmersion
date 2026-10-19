# Formatting lint

Articles, summaries and notes are Markdown. The formatting lint finds what will **not display as
written** — a table that will not render, an unpaired `**`, a broken link — before a reader does.

Articles, summaries and source notes are Markdown written by agents, and the page renders them with
**flexmark: CommonMark plus GFM pipe tables** (`server/MarkdownFlavor` — the same parser for the page
and for the check). The formatting lint parses each text with that parser, after the same `[[link]]`
rewrite the page does, and reports what will not display as written — a run of pipes the parser did
not make a table (no blank line before it, no `|---|` row, a delimiter row of the wrong width), `**`
or `` ` `` it could not pair (a space inside, an odd count, a starred reconstruction like `*Manu/*Yemo`
pairing into italics), a link it could not read (`[text] (url)`, an unclosed `(`, `<url`), a malformed
`[[wikilink]]` (one bracket short, escaped, padded, not a slug, an embed), pasted YAML frontmatter or
`key: value` headers, a ```` ```markdown ```` wrapper, an unclosed fence, indented prose that became
code, `##Heading`, a `---` under a paragraph (a heading, not a rule), list items glued to a paragraph,
raw HTML left open or unknown, double-escaped entities, literal `\n`, footnotes, math and
strikethrough (not rendered), and the suspicious: skipped heading levels, a second `#` title,
duplicated sections, a hand-written `## Sources cited`, bare citekeys and URLs, agent chatter
(`Here is the article…`) and placeholders (`TODO`, `[citation needed]`). Well-formed `[[citekey]]` /
`[[slug]]` links are the corpus's link syntax and never a problem.

Each problem is a `FormatIssue{rule, severity, field, line, column, excerpt, message, fix}`:
`error` = will not render as meant, `warning` = renders, probably not as meant. Where it shows up:

- every article read and write (`get_article`, `put_article`, `patch_article`, `GET/PUT/PATCH
  /api/articles/{slug}`) and every source write (`put_source`, `patch_source`) carries them;
  `strict` (MCP) / `?strict=true` (REST) / `--strict` (CLI) refuses a write whose text has an error;
- `histmersion article lint <slug> [--fix]`, `histmersion lint --source <citekey> [--fix]`,
  `histmersion lint --file x.md [--fix]` (before `article put`), and the `lint` MCP tool's `article` /
  `source` / `text`;
- the corpus rules `article-markdown` and `note-markdown` in `histmersion lint` (one line per problem,
  `- **<slug>** — body L12:3 error table-not-rendered — …`);
- the article and source pages: a folded "This article may not display correctly: N formatting
  problems" above the prose, which expands to the list.

`--fix` applies only repairs that never change a word — a blank line before a table or a list, the
space in `[text] (url)` or `** bold**`, `##Heading`, `\[\[x\]\]` unescaped, `[[x]` completed, a
pasted frontmatter block whose keys are the record's own fields removed, a ```` ```markdown ```` wrapper
or an indentation removed, `<…>` around a bare URL, `&amp;amp;` collapsed — as one patch guarded by the
`updatedAt` just read (the previous body stays an article version).
