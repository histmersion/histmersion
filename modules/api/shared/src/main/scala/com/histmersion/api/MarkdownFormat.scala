package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** FORMATTING problems in the Markdown an agent wrote — an article body or summary, a source's notes or summary — as
  * the server's renderer will show them.
  *
  * This file is the CONTRACT (the rule catalogue and the wire shapes, shared by the server, the CLI and the web UI).
  * The checks themselves run on the server (`server.MarkdownLint`), because they parse the text with the same flexmark
  * configuration the pages are rendered with: "a table is expected here, and the renderer made a paragraph of it" is a
  * fact about that parser, not a guess from a regular expression.
  */

/** How bad one problem is. `Error`: the text will NOT render as its writer meant (a table shown as pipes, literal `**`,
  * a link that is not one). `Warning`: it renders, but is probably not what was meant (a skipped heading level, a
  * duplicated section, agent chatter).
  */
enum FormatSeverity:
  case Error, Warning

object FormatSeverity:
  given WireEnumValues[FormatSeverity]:
    def all: Array[FormatSeverity] = FormatSeverity.values
    def wire(e: FormatSeverity): String = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[FormatSeverity] = FormatSeverity.values.find(_.toString.equalsIgnoreCase(s))
  given Encoder[FormatSeverity] = WireEnum.encoder
  given Decoder[FormatSeverity] = WireEnum.decoder
  given Schema[FormatSeverity] = WireEnum.schema

/** Which stored text a problem is in. */
enum FormatField:
  case Body, Summary, Notes

object FormatField:
  given WireEnumValues[FormatField]:
    def all: Array[FormatField] = FormatField.values
    def wire(e: FormatField): String = e.toString.toLowerCase(java.util.Locale.ROOT)
    def fromWire(s: String): Option[FormatField] = FormatField.values.find(_.toString.equalsIgnoreCase(s))
  given Encoder[FormatField] = WireEnum.encoder
  given Decoder[FormatField] = WireEnum.decoder
  given Schema[FormatField] = WireEnum.schema

/** The catalogue. `wire` is the id a report and `--rule` use; `severity` is the default (a check may lower it for a
  * milder variant); `fixable` says whether `--fix` can repair it without touching wording.
  */
enum FormatRule(val wire: String, val severity: FormatSeverity, val fixable: Boolean, val description: String):
  // ── the document as a whole ──────────────────────────────────────────────────────────────────
  case Frontmatter
      extends FormatRule(
        "frontmatter",
        FormatSeverity.Error,
        true,
        "a YAML `---` block or `key: value` header pasted into the text — it renders as a rule and a heading"
      )
  case WrappedInFence
      extends FormatRule(
        "wrapped-in-fence",
        FormatSeverity.Error,
        true,
        "the whole text wrapped in a ```markdown fence — it renders as one grey code block"
      )
  case UnclosedFence
      extends FormatRule(
        "unclosed-fence",
        FormatSeverity.Error,
        false,
        "a code fence that is never closed (or closed with a shorter/different fence) — everything after it is code"
      )
  case IndentedCode
      extends FormatRule(
        "indented-code",
        FormatSeverity.Error,
        true,
        "a paragraph indented by four spaces — it renders as a code block"
      )
  case LiteralEscape
      extends FormatRule(
        "literal-escape",
        FormatSeverity.Error,
        true,
        "a literal \\n or \\t (an escaped newline) — it shows as backslash-n"
      )
  case AgentChatter
      extends FormatRule(
        "agent-chatter",
        FormatSeverity.Warning,
        false,
        "a line addressed to the person who asked (\"Here is the article\", \"Sure!\", \"As an AI\") left in the text"
      )
  case Placeholder
      extends FormatRule(
        "placeholder",
        FormatSeverity.Warning,
        false,
        "a placeholder left in the text (TODO, XXX, [citation needed], <insert …>)"
      )
  // ── tables ───────────────────────────────────────────────────────────────────────────────────
  case TableNotRendered
      extends FormatRule(
        "table-not-rendered",
        FormatSeverity.Error,
        true,
        "rows of pipes that the renderer does not read as a table (no blank line before it, or no `|---|` row) — they show as text"
      )
  case TableColumns
      extends FormatRule(
        "table-columns",
        FormatSeverity.Warning,
        false,
        "a table row with more or fewer cells than the header — extra cells are dropped, missing ones are blank"
      )
  // ── inline markup ────────────────────────────────────────────────────────────────────────────
  case Emphasis
      extends FormatRule(
        "emphasis",
        FormatSeverity.Error,
        true,
        "`**`, `__` or a stray `*`/`_` the renderer could not pair (unbalanced, or with a space inside) — it shows literally"
      )
  case CodeSpan
      extends FormatRule(
        "code-span",
        FormatSeverity.Error,
        false,
        "an unpaired backtick (or a code span closed with a curly quote) — it shows literally"
      )
  case Strikethrough
      extends FormatRule(
        "strikethrough",
        FormatSeverity.Warning,
        false,
        "`~~struck~~` text — the renderer has no strikethrough; the tildes show literally"
      )
  case Math
      extends FormatRule(
        "math",
        FormatSeverity.Warning,
        false,
        "`$$…$$` or `\\(…\\)` math — the renderer does not typeset it"
      )
  case HtmlEntity
      extends FormatRule(
        "html-entity",
        FormatSeverity.Error,
        true,
        "a double-escaped entity (`&amp;amp;`, `&amp;lt;`) — it shows as `&amp;`"
      )
  case HtmlTag
      extends FormatRule(
        "html-tag",
        FormatSeverity.Warning,
        false,
        "raw HTML: an unclosed or unknown tag (an unclosed one swallows what follows)"
      )
  // ── links ────────────────────────────────────────────────────────────────────────────────────
  case LinkSyntax
      extends FormatRule(
        "link-syntax",
        FormatSeverity.Error,
        true,
        "a Markdown link the renderer could not read: `[text] (url)`, `[text](url` unclosed, `<url` unclosed"
      )
  case LinkEmpty
      extends FormatRule(
        "link-empty",
        FormatSeverity.Error,
        false,
        "a link with no text (invisible) or no target (goes nowhere)"
      )
  case BareUrl
      extends FormatRule(
        "bare-url",
        FormatSeverity.Warning,
        true,
        "a bare http(s) URL — the renderer does not make it clickable; wrap it in <…>"
      )
  case WikiPathLink
      extends FormatRule(
        "wiki-path-link",
        FormatSeverity.Warning,
        true,
        "a Markdown link to a wiki file or page (`(x.md)`, `(/article/x)`) — write [[x]] so the link graph sees it"
      )
  case Wikilink
      extends FormatRule(
        "wikilink",
        FormatSeverity.Error,
        true,
        "a malformed [[wikilink]]: one bracket short (`[[x]`, `[x]]`), escaped (`\\[\\[x\\]\\]`), padded (`[[ x ]]`), not a slug (`[[Two Words]]`) or an embed (`![[x]]`)"
      )
  case WikilinkUnresolved
      extends FormatRule(
        "wikilink-unresolved",
        FormatSeverity.Warning,
        false,
        "a [[wikilink]] that names no source and no article — it renders as plain text"
      )
  case BareCitekey
      extends FormatRule(
        "bare-citekey",
        FormatSeverity.Warning,
        false,
        "a citekey written without [[…]] — it is plain text, not a citation"
      )
  case Image
      extends FormatRule(
        "image",
        FormatSeverity.Error,
        false,
        "an image with no source, or pointing at a local file path the server cannot serve"
      )
  case Footnote
      extends FormatRule(
        "footnote",
        FormatSeverity.Error,
        false,
        "a footnote (`[^1]`) — the renderer has no footnotes, so the marker and the note show literally"
      )
  // ── headings ─────────────────────────────────────────────────────────────────────────────────
  case HeadingSyntax
      extends FormatRule(
        "heading-syntax",
        FormatSeverity.Error,
        true,
        "a heading the renderer does not read as one (`##Heading`), or one with a stray trailing `#`"
      )
  case SetextHeading
      extends FormatRule(
        "setext-heading",
        FormatSeverity.Error,
        true,
        "a `---` line right under a paragraph — the paragraph becomes a heading instead of getting a rule under it"
      )
  case HeadingLevel
      extends FormatRule(
        "heading-level",
        FormatSeverity.Warning,
        false,
        "a skipped heading level (## then ####), a second # title, or a heading inside a list item"
      )
  case DuplicateHeading
      extends FormatRule(
        "duplicate-heading",
        FormatSeverity.Warning,
        false,
        "the same heading twice at the same level — a section written twice"
      )
  case SourcesCitedSection
      extends FormatRule(
        "sources-cited-section",
        FormatSeverity.Warning,
        false,
        "a `## Sources cited` section in the body — the page generates that list from the [[citekey]] links"
      )
  // ── lists ────────────────────────────────────────────────────────────────────────────────────
  case ListGlued
      extends FormatRule(
        "list-glued",
        FormatSeverity.Error,
        true,
        "list items glued to the paragraph above (no blank line) — they render as one run-on paragraph"
      )
  case ListMarkers
      extends FormatRule(
        "list-markers",
        FormatSeverity.Warning,
        false,
        "a list whose bullet changes (`-` then `*`) or whose numbers jump — it splits into separate lists or renumbers"
      )

object FormatRule:
  given WireEnumValues[FormatRule]:
    def all: Array[FormatRule] = FormatRule.values
    def wire(e: FormatRule): String = e.wire
    def fromWire(s: String): Option[FormatRule] = FormatRule.values.find(_.wire == s.trim.toLowerCase)
  given Encoder[FormatRule] = WireEnum.encoder
  given Decoder[FormatRule] = WireEnum.decoder
  given Schema[FormatRule] = WireEnum.schema

  def fromWire(s: String): Option[FormatRule] = summon[WireEnumValues[FormatRule]].fromWire(s)

/** One problem. `line`/`column` are 1-based, in the stored text of `field`; `excerpt` is the offending text (trimmed,
  * at most ~80 characters); `message` says what will go wrong; `fix`, when present, says what `--fix` would change
  * (only for repairs that never touch wording).
  */
final case class FormatIssue(
    rule: FormatRule,
    severity: FormatSeverity,
    field: FormatField,
    line: Int,
    column: Int,
    excerpt: String,
    message: String,
    fix: Option[String] = None
) derives KindlingsCodecAsObject
object FormatIssue:
  given Schema[FormatIssue] = KindlingsSchema.derived[FormatIssue].schema

/** The formatting report of one subject (an article slug, a citekey, or `text`). `fixed` lists the repairs `--fix`
  * applied (empty for a read-only check); `text` is the repaired text when the subject was a text sent to
  * `POST /api/lint/markdown` with `fix`.
  */
final case class FormatReport(
    subject: String,
    issues: List[FormatIssue],
    fixed: List[String] = Nil,
    text: Option[String] = None
) derives KindlingsCodecAsObject:
  def errors: Int = issues.count(_.severity == FormatSeverity.Error)
  def warnings: Int = issues.count(_.severity == FormatSeverity.Warning)
object FormatReport:
  given Schema[FormatReport] = KindlingsSchema.derived[FormatReport].schema

/** `POST /api/lint/markdown`: check Markdown that is not stored yet (a file an agent is about to `article put`). `fix`
  * returns the repaired text in [[FormatReport.text]].
  */
final case class FormatCheck(text: String, field: FormatField = FormatField.Body, fix: Boolean = false)
    derives KindlingsCodecAsObject
object FormatCheck:
  given Schema[FormatCheck] = KindlingsSchema.derived[FormatCheck].schema

/** How a formatting report reads on every Markdown surface (MCP, CLI). */
object FormatMarkdown:

  def problems(n: Int): String = Plural.count(n, "formatting problem")

  /** One line per issue: `body L12:3 error table-not-rendered — what goes wrong · "excerpt" · fix: …`. */
  def line(i: FormatIssue): String =
    val sev = summon[WireEnumValues[FormatSeverity]].wire(i.severity)
    val fld = summon[WireEnumValues[FormatField]].wire(i.field)
    val ex = if i.excerpt.isEmpty then "" else s" · `${i.excerpt.replace("`", "'")}`"
    val fx = i.fix.fold("")(f => s" · fix: $f")
    s"$fld L${i.line}:${i.column} $sev ${i.rule.wire} — ${i.message}$ex$fx"

  /** The section appended to an article or source after a read or a write: nothing when clean. */
  def notice(issues: List[FormatIssue]): String =
    if issues.isEmpty then ""
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val head = s"## Formatting: ${problems(issues.size)}" +
        (if errs > 0 then s" ($errs will not display as intended)" else "")
      val tip = if issues.exists(_.fix.nonEmpty) then
        "\n\n_Repairs marked `fix:` are applied by `histmersion article lint <slug> --fix` / `lint --source <citekey> --fix`._"
      else ""
      s"\n\n$head\n\n${issues.map(i => s"- ${line(i)}").mkString("\n")}$tip"

  def report(r: FormatReport): String =
    val fixed = if r.fixed.isEmpty then "" else r.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    if r.issues.isEmpty then s"$fixed### ${r.subject}: no formatting problems"
    else
      val head = s"### ${r.subject}: ${problems(r.issues.size)} (${r.errors} error(s), ${r.warnings} warning(s))"
      s"$fixed$head\n\n${r.issues.map(i => s"- ${line(i)}").mkString("\n")}"
