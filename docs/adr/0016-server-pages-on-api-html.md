# 0016. The server's pages are built from api.Html, and search marks only text

- Status: accepted
- Date: 2026-11-21

## Context

The server renders its own HTML pages (`/source/{ck}`, `/article/{slug}`) besides the Scala.js app. Their data — titles,
credits, OCR'd bodies, an agent's notes — is text from the database, and the pages used to be built as strings with an
escape call at each site; two sites had none (W5). Highlighting a search's terms rewrote that markup with regular
expressions.

## Decision

- **One markup type.** `HtmlRender`, `Pages` and `ArticleHtml` write `html"…"` and return `api.Html`: every value from
  the database is a text the interpolator escapes, only an `Html` is spliced as it is. The routes hand `.markup` to the
  HTTP body. A page's title is data and goes through the same escaping.
- **Two surfaces.** `HtmlRender` holds the atoms of the human pages, one shape per thing, so a source reference looks
  the same in an article's prose, its "Sources cited" footer, a source's "Cites / Cited by" list and the backlinks. The
  Markdown in `core` (`Markdown`, `ArticleMarkdown`) is the agents' surface (MCP, CLI) and stays separate: what reads
  well to an agent (`[[citekey]]`, "an empty result is not evidence of agreement") is not what a reader wants. A readable
  label is "Cline 2014", or the title when a source has no authors, never a bare year.
- **Prose stays Markdown until it is rendered.** `Pages.linkProse` replaces every `[[wikilink]]` with its typed anchor
  (raw inline HTML, which flexmark passes through and `MarkdownHtml` keeps) and returns Markdown; it becomes `Html`
  only through `MarkdownHtml.render`. Wikilinks resolve through one lookup for the whole page (`PageBuilder`).
- **Markup derived from markup lives on api.Html.** `Html.lazyImages` adds `loading="lazy"` to every `<img>` that does
  not say how it loads (and to nothing else). `Html.marked` wraps each match of a pattern in the page's TEXT in
  `<mark id="hl<n>">` (n from 1, in document order); a tag with its attributes, the content of `<script>` and `<style>`
  and an entity (`&amp;`) are copied as they are, so a term inside an address or an entity never breaks the markup. The
  ids are what the web page's `InPage.nextMatch` steps through. It appends with `java.lang.StringBuilder`, because
  Scala's has no `append(CharSequence, from, to)` and would silently auto-tuple the three arguments into `append(Any)`.
- **What a search highlights.** `core`'s `Highlight` gives only the pattern: case-insensitive, any term at a word start,
  extended over the rest of the word so a stemmed hit ("egypt" → "Egyptian") is highlighted whole; a phrase allows any
  run of white space. A pattern used in Scala.js has no Unicode flag: Scala.js refuses `\b` with Unicode case before
  ES2018.
- **The bytes are pinned.** `PagesOracleSuite` records a source page (whole, without its text, with its text missing),
  an article page and every atom over data holding `&`, `<`, `"`, underscores, a figure, findings, edges of every kind,
  formatting problems and a review. It was recorded on the string-built pages before the move, so what was escaped then
  is escaped now, byte for byte (`HISTMERSION_UPDATE_ORACLE=1` rewrites it).
- **The app's page only at its own addresses.** The web UI's shell is served at `WebPaths` only; every other GET (a typo,
  a probe, a missing asset) is a 404, not the whole page with 200.

## Consequences

Escaping is no longer a choice at each site. The one remaining `…Html` newtypes over `String` (html-newtype 5 → 2) are
`RepositoryLanding` and `MediaText`.

## Code

`api.Html` (`html`, `lazyImages`, `marked`, `markText`), `core` `domain.Highlight`, `server.HtmlRender`,
`server.Pages`, `server.ArticleHtml`, `server.MarkdownHtml`, `server.HttpApi` (`index`), `research.articles.PageBuilder`,
`ArticleRoutes`; tests `PagesOracleSuite`, `HtmlSuite`, `HighlightSuite`.
