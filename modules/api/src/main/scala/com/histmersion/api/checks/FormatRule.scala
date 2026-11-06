package com.histmersion.api.checks
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given

enum FormatRule(val wire: FormatRule.Wire, val severity: FormatSeverity, val fixable: FormatRule.Fixable, val description: FormatRule.Description):
  case Frontmatter
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("frontmatter"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a YAML `---` block or `key: value` header pasted into the text — it renders as a rule and a heading")
      )
  case WrappedInFence
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("wrapped-in-fence"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("the whole text wrapped in a ```markdown fence — it renders as one grey code block")
      )
  case UnclosedFence
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("unclosed-fence"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a code fence that is never closed (or closed with a shorter/different fence) — everything after it is code")
      )
  case IndentedCode
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("indented-code"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a paragraph indented by four spaces — it renders as a code block")
      )
  case LiteralEscape
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("literal-escape"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a literal \\n or \\t (an escaped newline) — it shows as backslash-n")
      )
  case AgentChatter
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("agent-chatter"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a line addressed to the person who asked (\"Here is the article\", \"Sure!\", \"As an AI\") left in the text")
      )
  case Placeholder
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("placeholder"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a placeholder left in the text (TODO, XXX, [citation needed], <insert …>)")
      )
  case TableNotRendered
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("table-not-rendered"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("rows of pipes that the renderer does not read as a table (no blank line before it, or no `|---|` row) — they show as text")
      )
  case TableColumns
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("table-columns"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a table row with more or fewer cells than the header — extra cells are dropped, missing ones are blank")
      )
  case Emphasis
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("emphasis"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("`**`, `__` or a stray `*`/`_` the renderer could not pair (unbalanced, or with a space inside) — it shows literally")
      )
  case CodeSpan
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("code-span"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("an unpaired backtick (or a code span closed with a curly quote) — it shows literally")
      )
  case Strikethrough
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("strikethrough"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("`~~struck~~` text — the renderer has no strikethrough; the tildes show literally")
      )
  case Math
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("math"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("`$$…$$` or `\\(…\\)` math — the renderer does not typeset it")
      )
  case HtmlEntity
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("html-entity"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a double-escaped entity (`&amp;amp;`, `&amp;lt;`) — it shows as `&amp;`")
      )
  case HtmlTag
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("html-tag"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("raw HTML: an unclosed or unknown tag (an unclosed one swallows what follows)")
      )
  case LinkSyntax
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("link-syntax"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a Markdown link the renderer could not read: `[text] (url)`, `[text](url` unclosed, `<url` unclosed")
      )
  case LinkEmpty
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("link-empty"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a link with no text (invisible) or no target (goes nowhere)")
      )
  case BareUrl
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("bare-url"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a bare http(s) URL — the renderer does not make it clickable; wrap it in <…>")
      )
  case WikiPathLink
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("wiki-path-link"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a Markdown link to a wiki file or page (`(x.md)`, `(/article/x)`) — write [[x]] so the link graph sees it")
      )
  case Wikilink
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("wikilink"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a malformed [[wikilink]]: one bracket short (`[[x]`, `[x]]`), escaped (`\\[\\[x\\]\\]`), padded (`[[ x ]]`), not a slug (`[[Two Words]]`) or an embed (`![[x]]`)")
      )
  case WikilinkUnresolved
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("wikilink-unresolved"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a [[wikilink]] that names no source and no article — it renders as plain text")
      )
  case BareCitekey
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("bare-citekey"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a citekey written without [[…]] — it is plain text, not a citation")
      )
  case Image
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("image"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("an image with no source, or pointing at a local file path the server cannot serve")
      )
  case Footnote
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("footnote"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a footnote (`[^1]`) — the renderer has no footnotes, so the marker and the note show literally")
      )
  case HeadingSyntax
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("heading-syntax"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a heading the renderer does not read as one (`##Heading`), or one with a stray trailing `#`")
      )
  case SetextHeading
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("setext-heading"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("a `---` line right under a paragraph — the paragraph becomes a heading instead of getting a rule under it")
      )
  case HeadingLevel
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("heading-level"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a skipped heading level (## then ####), a second # title, or a heading inside a list item")
      )
  case DuplicateHeading
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("duplicate-heading"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("the same heading twice at the same level — a section written twice")
      )
  case SourcesCitedSection
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("sources-cited-section"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a `## Sources cited` section in the body — the page generates that list from the [[citekey]] links")
      )
  case ListGlued
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("list-glued"),
        FormatSeverity.Error,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(true),
        _root_.com.histmersion.api.checks.FormatRule.Description("list items glued to the paragraph above (no blank line) — they render as one run-on paragraph")
      )
  case ListMarkers
      extends FormatRule(
        _root_.com.histmersion.api.checks.FormatRule.Wire("list-markers"),
        FormatSeverity.Warning,
        _root_.com.histmersion.api.checks.FormatRule.Fixable(false),
        _root_.com.histmersion.api.checks.FormatRule.Description("a list whose bullet changes (`-` then `*`) or whose numbers jump — it splits into separate lists or renumbers")
      )

object FormatRule:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fixable = Fixable.T
  object Fixable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  given WireEnumValues[FormatRule]:
    def all: Array[FormatRule] = FormatRule.values
    def wire(e: FormatRule): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[FormatRule] = FormatRule.values.find(_.wire == _root_.com.histmersion.api.checks.FormatRule.Wire((s).value.trim.toLowerCase))
  given Encoder[FormatRule] = WireEnum.encoder
  given Decoder[FormatRule] = WireEnum.decoder
  given Schema[FormatRule] = WireEnum.schema

  def fromWire(s: com.histmersion.api.checks.FormatRule.Wire): Option[FormatRule] = summon[WireEnumValues[FormatRule]].fromWire(s)
