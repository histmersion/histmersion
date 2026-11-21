package com.histmersion
package api

import scala.annotation.tailrec
import scala.quoted.*

/** Markup a page may put into its document as it is. A value is made in only three ways, so a text that came from
  * outside (a title, a credit, an OCR'd body, an agent's notes) never becomes markup by accident:
  *
  *   - [[Html.text]] escapes a text;
  *   - the `html"…"` interpolator ([[Html.html]]) takes the code's own literal markup and escapes every spliced value
  *     but another `Html`;
  *   - [[Html.sanitize]] keeps of foreign markup only the elements and attributes of an allow-list, with link and image
  *     addresses of an allowed scheme ([[Href]]); everything else is shown as text or dropped, never run.
  *
  * The web page's DOM sinks and the server's HTML pages take this type, never a `String`.
  */
type Html = Html.T

object Html:
  opaque type T = String

  given CanEqual[Html, Html] = CanEqual.derived

  val empty: T = ""

  /** The text as it reads: every character that could start markup or end a (double-quoted) attribute value escaped.
    * The interpolator refuses a splice in a single-quoted attribute, so an apostrophe needs no escaping.
    */
  def text(s: String): T = escape(s, apostrophe = false)

  def concat(parts: Iterable[T]): T = parts.mkString

  def join(parts: Iterable[T], separator: T): T = parts.mkString(separator)

  extension (h: T)
    def ++(other: T): T = h + other
    def isEmpty: Boolean = h.isEmpty
    def nonEmpty: Boolean = !h.isEmpty

    /** The markup, for the one place that hands it to the outside: a DOM sink, an HTTP body. */
    def markup: String = h

  /** What the interpolator splices besides an `Html` (spliced as it is): a text or a number, escaped. */
  trait Splice[A]:
    def html(a: A): T

  object Splice:
    given string: Splice[String] = s => text(s)
    given int: Splice[Int] = i => i.toString
    given long: Splice[Long] = l => l.toString
    given double: Splice[Double] = d => d.toString
    given href: Splice[Href] = u => text(u.address)

  /** `html"<b>$name</b>"`: the literal parts are the code's own markup, every splice goes through its [[Splice]] (a
    * `String` is escaped). The parts must be literals: a `StringContext` built at run time does not compile.
    */
  extension (inline sc: StringContext) inline def html(inline args: Any*): Html = ${ interpolate('sc, 'args) }

  private def interpolate(sc: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[Html] =
    import quotes.reflect.*
    val parts: Seq[String] = sc match
      case '{ StringContext(${ Varargs(ps) }*) } =>
        ps.map(p => p.value.getOrElse(report.errorAndAbort("html\"…\": the literal parts must be constants", p)))
      case _ => report.errorAndAbort("html\"…\" works on a string literal only", sc)
    if parts.init.exists(p => p.endsWith("'") || p.matches("(?s).*=\\s*")) then
      report.errorAndAbort("html\"…\": a splice in an attribute goes inside double quotes (attr=\"$value\")", sc)
    // compared by symbol: inside this object the opaque `T` is a String, so an implicit search here would take a
    // String for an Html
    val markup = Symbol.requiredModule("com.histmersion.api.Html").typeMember("T")
    // a newtype (an Iron refined type) is its base value at run time: spliced as that value's text, escaped
    val refined = Symbol.requiredClass("io.github.iltotore.iron.RefinedType")
    def newtype(t: TypeRepr): Boolean =
      val s = t.dealiasKeepOpaques.typeSymbol
      s.flags.is(Flags.Opaque) && s.owner.isClassDef && s.owner.typeRef.derivesFrom(refined)
    val pieces: Seq[Expr[Html]] = args match
      case Varargs(as) =>
        as.map { a =>
          val tpe = a.asTerm.tpe.widen
          if tpe.dealiasKeepOpaques.typeSymbol.equals(markup) then a.asExprOf[Html]
          // a text or a number refined by Iron (`x.value` of a newtype) is a subtype of its base
          else if tpe <:< TypeRepr.of[String] then '{ text(${ a.asExprOf[String] }) }
          else if List(TypeRepr.of[Int], TypeRepr.of[Long], TypeRepr.of[Double], TypeRepr.of[Boolean]).exists(tpe <:< _)
          then '{ text(${ a.asExprOf[Any] }.toString) }
          else
            tpe.asType match
              case '[t] =>
                Expr.summon[Splice[t]] match
                  case Some(s)              => '{ $s.html(${ a.asExprOf[t] }) }
                  case None if newtype(tpe) => '{ text(${ a.asExprOf[t] }.toString) }
                  case None                 =>
                    report.errorAndAbort(
                      s"html\"…\" cannot splice a ${Type.show[t]}: splice a text, a number or a newtype (escaped), or an Html",
                      a
                    )
        }
      case _ => report.errorAndAbort("html\"…\": the splices must be written in place", args)
    val all = pieces.zip(parts.tail).foldLeft(Expr(parts.head)) { case (acc, (piece, part)) =>
      '{ $acc + $piece + ${ Expr(part) } }
    }
    '{ $all.asInstanceOf[Html] }

  /** The atoms every surface draws one way — the server's pages and the web page alike (cross-platform: no JVM
    * `URLEncoder`): the one source anchor, the one article anchor, the one confidence badge.
    */
  object Atoms:
    def esc(s: String): String = escape(s, apostrophe = false)

    def segment(s: String): String =
      val unreserved = (c: Char) => c.isLetterOrDigit && c < 128 || "-._~".contains(c)
      s.flatMap { c =>
        if unreserved(c) then c.toString
        else c.toString.getBytes("UTF-8").map(b => f"%%${b & 0xff}%02X").mkString
      }

    def sourceLink(citekey: library.Citekey, doi: Option[library.Doi], label: String): T =
      val doiAttr = doi.map(_.value.trim).filter(_.nonEmpty).fold("")(d => s""" data-doi="${esc(d)}"""")
      s"""<a class="src-ref" data-ck="${esc(citekey.value)}"$doiAttr href="/source/${esc(
          segment(citekey.value)
        )}">${esc(label)}</a>"""

    def articleLink(slug: research.ReviewGoal.Slug, label: String): T =
      s"""<a class="art-ref" data-slug="${esc(slug.value)}" href="/article/${esc(segment(slug.value))}">${esc(
          label
        )}</a>"""

    def confidenceBadge(c: library.Confidence): T =
      val wire = c.toString.toLowerCase
      s"""<span class="conf-badge conf-$wire" title="${esc(ConfidenceStyle.label(c))}">""" +
        s"""${ConfidenceStyle.icon(c).markup}<span class="conf-text">$wire</span></span>"""

  private val BareImg = """<img(?![^>]*\bloading=)\s""".r

  def lazyImages(h: T): T =
    BareImg.replaceAllIn(h, _ => java.util.regex.Matcher.quoteReplacement("""<img loading="lazy" """))

  def marked(h: T, pattern: scala.util.matching.Regex): (T, Int) = markText(h, pattern)

  private[api] def markText(src: String, pattern: scala.util.matching.Regex): (String, Int) =
    if src.isEmpty then (src, 0)
    else
      val sb = new java.lang.StringBuilder(src.length + 32)
      @tailrec def loop(i: Int, n: Int): Int =
        if i >= src.length then n
        else if src.charAt(i) == '<' then
          val skipTo = verbatimEnd(src, i)
          val _ = sb.append(src, i, skipTo)
          loop(skipTo, n)
        else
          val end = src.indexOf('<', i) match
            case -1 => src.length
            case k  => k
          val added = marks(src.substring(i, end), pattern, sb, n)
          loop(end, n + added)
      val n = loop(0, 0)
      (sb.toString, n)

  private def marks(text: String, re: scala.util.matching.Regex, sb: java.lang.StringBuilder, soFar: Int): Int =
    textPieces(text).foldLeft(0) {
      case (n, Left(entity)) =>
        val _ = sb.append(entity)
        n
      case (n, Right(plain)) =>
        val (last, added) = re.findAllMatchIn(plain).foldLeft((0, n)) { case ((last, k), m) =>
          if m.start >= last then
            val _ = sb
              .append(plain, last, m.start)
              .append("<mark id=\"hl")
              .append(soFar + k + 1)
              .append("\">")
              .append(plain, m.start, m.end)
              .append("</mark>")
            (m.end, k + 1)
          else (last, k)
        }
        val _ = sb.append(plain, last, plain.length)
        added
    }

  private def verbatimEnd(html: String, at: Int): Int =
    val close =
      if html.regionMatches(true, at, "<script", 0, 7) then Some("</script>")
      else if html.regionMatches(true, at, "<style", 0, 6) then Some("</style>")
      else None
    close match
      case Some(tag) =>
        html.toLowerCase.indexOf(tag, at) match
          case -1 => html.length
          case k  => k + tag.length
      case None =>
        html.indexOf('>', at) match
          case -1 => html.length
          case k  => k + 1

  private val TextEntity = """&(?:#\d+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]{1,31});""".r

  private def textPieces(text: String): List[Either[String, String]] =
    val (pieces, last) =
      TextEntity.findAllMatchIn(text).foldLeft((List.empty[Either[String, String]], 0)) { case ((acc, last), m) =>
        val before = if m.start > last then List(Right(text.substring(last, m.start))) else Nil
        (Left(m.matched) :: before.reverse ::: acc, m.end)
      }
    (if last < text.length then Right(text.substring(last)) :: pieces else pieces).reverse

  /** Foreign markup through the allow-list. The tags of [[Allowed]] with the attributes listed for them (and
    * [[GlobalAttributes]]) are written again in one form: lower-case names, double-quoted escaped values, an `href` or
    * `src` of a refused scheme dropped ([[Href]]). A tag the allow-list refuses that a browser knows (`<script>`,
    * `<iframe>`, `<svg>`, or an allowed one with an `on…` attribute) is shown as text; one no browser knows (a
    * publisher's `<etal>`, `<jats:p>`) is dropped and its text kept, as a browser shows it; a comment is dropped. Text
    * passes as it is, every `<` that starts no kept tag escaped. What it returns, it returns unchanged.
    */
  def sanitize(untrusted: String): T =
    val out = new java.lang.StringBuilder(untrusted.length + 16)
    @tailrec def loop(i: Int): Unit =
      if i < untrusted.length then
        untrusted.indexOf('<', i) match
          case -1 => val _ = out.append(untrusted, i, untrusted.length)
          case lt =>
            val _ = out.append(untrusted, i, lt)
            loop(markupAt(untrusted, lt, out))
    loop(0)
    out.toString

  /** The tags a page keeps, each with the attributes it keeps besides [[GlobalAttributes]]. */
  val Allowed: Map[String, Set[String]] =
    List(
      "abbr",
      "article",
      "aside",
      "b",
      "bdi",
      "bdo",
      "blockquote",
      "br",
      "caption",
      "cite",
      "code",
      "dd",
      "del",
      "dfn",
      "div",
      "dl",
      "dt",
      "em",
      "figcaption",
      "figure",
      "footer",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "header",
      "hr",
      "i",
      "ins",
      "kbd",
      "mark",
      "p",
      "pre",
      "q",
      "rp",
      "rt",
      "ruby",
      "s",
      "samp",
      "section",
      "small",
      "span",
      "strong",
      "sub",
      "summary",
      "sup",
      "table",
      "tbody",
      "tfoot",
      "thead",
      "tr",
      "u",
      "ul",
      "var",
      "wbr"
    ).map(_ -> Set.empty[String]).toMap ++ Map(
      "a" -> Set("href", "rel", "target"),
      "img" -> Set("src", "alt", "width", "height", "loading"),
      "ol" -> Set("start", "reversed", "type"),
      "li" -> Set("value"),
      "th" -> Set("align", "colspan", "rowspan", "scope"),
      "td" -> Set("align", "colspan", "rowspan"),
      "col" -> Set("span"),
      "colgroup" -> Set("span"),
      "details" -> Set("open"),
      "time" -> Set("datetime")
    )

  /** The attributes every kept tag may carry: the inert ones, and the `data-` attributes the web page's handlers read
    * (a source or article link's preview, a chip's filter, the match counter).
    */
  val GlobalAttributes: Set[String] = Set(
    "class",
    "id",
    "title",
    "lang",
    "dir",
    "role",
    "aria-hidden",
    "aria-label",
    "data-ck",
    "data-doi",
    "data-slug",
    "data-field",
    "data-value",
    "data-matches"
  )

  private val Void = Set("br", "hr", "img", "wbr", "col")

  /** Elements a browser knows that the allow-list refuses: shown as text, so nothing they held is lost or run. A name
    * outside both lists is no element a browser draws (it shows the text and ignores the tag): it is dropped.
    */
  private val Refused: Set[String] = Set(
    "address",
    "applet",
    "area",
    "audio",
    "base",
    "basefont",
    "bgsound",
    "big",
    "blink",
    "body",
    "button",
    "canvas",
    "center",
    "data",
    "datalist",
    "dialog",
    "embed",
    "fieldset",
    "font",
    "form",
    "frame",
    "frameset",
    "head",
    "hgroup",
    "html",
    "iframe",
    "image",
    "input",
    "isindex",
    "keygen",
    "label",
    "legend",
    "link",
    "listing",
    "main",
    "map",
    "marquee",
    "math",
    "menu",
    "meta",
    "meter",
    "nav",
    "nobr",
    "noembed",
    "noframes",
    "noscript",
    "object",
    "optgroup",
    "option",
    "output",
    "param",
    "picture",
    "plaintext",
    "portal",
    "progress",
    "script",
    "search",
    "select",
    "slot",
    "source",
    "strike",
    "style",
    "svg",
    "template",
    "textarea",
    "title",
    "track",
    "tt",
    "video",
    "xmp"
  )

  private def escape(s: String, apostrophe: Boolean): String =
    val sb = new java.lang.StringBuilder(s.length + 8)
    s.foreach(c => escapeChar(c, apostrophe, sb))
    sb.toString

  private def escapeChar(c: Char, apostrophe: Boolean, sb: java.lang.StringBuilder): Unit =
    val _ = c match
      case '&'                => sb.append("&amp;")
      case '<'                => sb.append("&lt;")
      case '>'                => sb.append("&gt;")
      case '"'                => sb.append("&quot;")
      case '\'' if apostrophe => sb.append("&#39;")
      case other              => sb.append(other)

  final private case class Attribute(name: String, value: Option[String])
  final private case class Tag(
      closing: Boolean,
      name: String,
      attributes: List[Attribute],
      selfClosing: Boolean,
      end: Int
  )

  /** At a `<`: writes what it starts (a kept tag, its text, or nothing) and returns where the text goes on. */
  private def markupAt(s: String, lt: Int, out: java.lang.StringBuilder): Int =
    if s.startsWith("<!--", lt) then
      s.indexOf("-->", lt + 4) match
        case -1  => val _ = out.append("&lt;"); lt + 1
        case end => end + 3
    else
      tagAt(s, lt) match
        case None =>
          val _ = out.append("&lt;")
          lt + 1
        case Some(tag) =>
          val raw = s.substring(lt, tag.end)
          val _ = Allowed.get(tag.name) match
            case Some(_) if tag.closing                                 => out.append(s"</${tag.name}>")
            case Some(own) if tag.attributes.forall(a => keeps(own, a)) => out.append(written(tag))
            case Some(_)                                                => out.append(escape(raw, apostrophe = false))
            case None if Refused(tag.name)                              => out.append(escape(raw, apostrophe = false))
            case None                                                   => out
          tag.end

  /** The one `target` a link may have: a new tab. Any other value (a named window, `_parent`, `_top`, a value with
    * spaces) refuses the tag, as any other refused attribute does.
    */
  val BlankTarget = "_blank"

  /** The `rel` tokens every link with [[BlankTarget]] ends with, whatever it said: the opened page gets no handle on
    * this one (`window.opener`) and no Referer.
    */
  val NoOpener = "noopener noreferrer"

  private val NoOpenerTokens: List[String] = NoOpener.split(' ').toList

  /** The `rel` of a link that opens a new tab: its own tokens (lower-case, once each, in their order) without `opener`,
    * then [[NoOpener]]. Its other tokens (`nofollow`, `ugc`, …) are kept, and written again the value is the same.
    */
  private def blankRel(rel: Option[String]): String =
    val own = rel.toList
      .flatMap(v => decoded(v).split("[ \t\n\f\r]+").toList)
      .map(_.toLowerCase(java.util.Locale.ROOT))
      .filter(t => t.nonEmpty && t != "opener" && !NoOpenerTokens.contains(t))
      .distinct
    escape((own ++ NoOpenerTokens).mkString(" "), apostrophe = false)

  private def keeps(own: Set[String], attribute: Attribute): Boolean =
    (GlobalAttributes(attribute.name) || own(attribute.name)) &&
      (attribute.name != "target" || attribute.value.exists(v => decoded(v).equalsIgnoreCase(BlankTarget)))

  private def written(tag: Tag): String =
    val blank = tag.name == "a" && tag.attributes.exists(_.name == "target")
    val unique = tag.attributes.distinctBy(_.name)
    val kept = unique.filterNot(a => blank && a.name == "rel").flatMap { a =>
      a.value match
        case None    => Some(a.name)
        case Some(v) =>
          val shown = (tag.name, a.name) match
            case ("a", "href")   => Href.link(decoded(v)).map(h => escape(h.address, apostrophe = false))
            case ("a", "target") => Some(BlankTarget)
            case ("img", "src")  => Href.image(decoded(v)).map(h => escape(h.address, apostrophe = false))
            case _               => Some(reEscaped(v))
          shown.map(x => s"""${a.name}="$x"""")
    }
    val forced = if blank then List(s"""rel="${blankRel(unique.find(_.name == "rel").flatMap(_.value))}"""") else Nil
    s"<${tag.name}${(kept ++ forced).map(" " + _).mkString}${if tag.selfClosing && Void(tag.name) then " /" else ""}>"

  private val NamedEntities: Map[String, String] =
    Map("amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'", "nbsp" -> " ")

  private val Entity = """&(?:#(\d{1,7});?|#[xX]([0-9a-fA-F]{1,6});?|([A-Za-z][A-Za-z0-9]{0,31});)""".r

  private def codePoint(n: Int): String =
    if n == 0 || n > 0x10ffff || (n >= 0xd800 && n <= 0xdfff) then "�" else new String(Character.toChars(n))

  private def entityText(m: scala.util.matching.Regex.Match): String =
    if m.group(1) != null then codePoint(m.group(1).toInt)
    else if m.group(2) != null then codePoint(Integer.parseInt(m.group(2), 16))
    else NamedEntities.getOrElse(m.group(3), m.matched)

  /** An address as the browser reads it, for the entities it decodes the same way. Any other named one stays as
    * written, and is escaped on the way out: what the browser reads is exactly what was checked.
    */
  private def decoded(v: String): String =
    Entity.replaceAllIn(v, m => java.util.regex.Matcher.quoteReplacement(entityText(m)))

  /** A value that is no address, written again: an entity this does not decode is kept (a browser reads it as the same
    * text), every other character that could end the value escaped.
    */
  private def reEscaped(v: String): String =
    val sb = new java.lang.StringBuilder(v.length + 8)
    @tailrec def loop(i: Int): Unit =
      if i < v.length then
        if v.charAt(i) == '&' then
          Entity.findPrefixMatchOf(v.substring(i)) match
            case Some(m) =>
              val text = entityText(m)
              if text == m.matched then sb.append(m.matched)
              else text.foreach(c => escapeChar(c, apostrophe = false, sb))
              loop(i + m.matched.length)
            case None =>
              val _ = sb.append("&amp;")
              loop(i + 1)
        else
          escapeChar(v.charAt(i), apostrophe = false, sb)
          loop(i + 1)
    loop(0)
    sb.toString

  private def isSpace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'
  private def isNameStart(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
  private def isNameChar(c: Char): Boolean = isNameStart(c) || (c >= '0' && c <= '9') || c == '-' || c == ':'
  private def isAttributeNameChar(c: Char): Boolean = isNameChar(c) || c == '_' || c == '.'

  private def firstFrom(s: String, from: Int)(p: Char => Boolean): Int =
    @tailrec def loop(i: Int): Int = if i >= s.length || p(s.charAt(i)) then i else loop(i + 1)
    loop(from)

  /** A tag in its strict form only: a name, then attributes each after white space (a value double-, single- or
    * unquoted), then `>` or `/>`. Anything else is no tag, and its `<` is text.
    */
  private def tagAt(s: String, lt: Int): Option[Tag] =
    val n = s.length
    val closing = lt + 1 < n && s.charAt(lt + 1) == '/'
    val nameStart = if closing then lt + 2 else lt + 1
    if nameStart >= n || !isNameStart(s.charAt(nameStart)) then None
    else
      val nameEnd = firstFrom(s, nameStart)(c => !isNameChar(c))
      val name = s.substring(nameStart, nameEnd).toLowerCase(java.util.Locale.ROOT)
      def ends(at: Int, acc: List[Attribute]): Option[Tag] =
        if at < n && s.charAt(at) == '>' then Some(Tag(closing, name, acc.reverse, selfClosing = false, at + 1))
        else if s.startsWith("/>", at) then Some(Tag(closing, name, acc.reverse, selfClosing = true, at + 2))
        else None
      @tailrec def attributes(i: Int, acc: List[Attribute]): Option[Tag] =
        val j = firstFrom(s, i)(c => !isSpace(c))
        ends(j, acc) match
          case found @ Some(_) => found
          case None            =>
            if j >= n || j == i || closing || !isAttributeNameChar(s.charAt(j)) then None
            else
              val nameEndAt = firstFrom(s, j)(c => !isAttributeNameChar(c))
              val attribute = s.substring(j, nameEndAt).toLowerCase(java.util.Locale.ROOT)
              val eq = firstFrom(s, nameEndAt)(c => !isSpace(c))
              if eq >= n || s.charAt(eq) != '=' then attributes(nameEndAt, Attribute(attribute, None) :: acc)
              else
                val v = firstFrom(s, eq + 1)(c => !isSpace(c))
                valueAt(s, v) match
                  case None                => None
                  case Some((value, next)) => attributes(next, Attribute(attribute, Some(value)) :: acc)
      val after = if nameEnd < n then s.charAt(nameEnd) else ' '
      if nameEnd >= n || !(isSpace(after) || after == '>' || s.startsWith("/>", nameEnd)) then None
      else attributes(nameEnd, Nil)

  /** An attribute value at `v` and where the tag goes on after it; `None` when it is not one in the strict form. */
  private def valueAt(s: String, v: Int): Option[(String, Int)] =
    val n = s.length
    if v >= n then None
    else
      val q = s.charAt(v)
      if q == '"' || q == '\'' then
        s.indexOf(q, v + 1) match
          case -1    => None
          case close =>
            val next = close + 1
            if next < n && !isSpace(s.charAt(next)) && s.charAt(next) != '>' && !s.startsWith("/>", next) then None
            else Some((s.substring(v + 1, close), next))
      else
        val end = firstFrom(s, v)(c => isSpace(c) || "\"'=<>`".indexOf(c.toInt) >= 0)
        if end == v || (end < n && "\"'=<`".indexOf(s.charAt(end).toInt) >= 0) then None
        else Some((s.substring(v, end), end))
