package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatRule(val wire: String, val severity: FormatSeverity, val fixable: Boolean, val description: String):
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
