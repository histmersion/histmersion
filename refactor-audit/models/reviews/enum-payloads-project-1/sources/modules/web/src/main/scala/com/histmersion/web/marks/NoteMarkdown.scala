package com.histmersion.web.marks

/** The little Markdown a note is written in, read for its preview: headings (`#`), paragraphs, lists (`-`, `*`, `1.`),
  * quotes (`>`), code blocks (```), and in a line `**strong**`, `*emphasis*`, `` `code` ``, `[text](https://…)` and
  * `[[citekey]]`. Anything else is plain text. Pure: the page builds the elements from this, never HTML from the text.
  */
object NoteMarkdown:

  type SafeHrefResult = SafeHrefResult.T
  object SafeHrefResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SafeHrefH = SafeHrefH.T
  object SafeHrefH extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksText = BlocksText.T
  object BlocksText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartsResult = StartsResult.T
  object StartsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartsL = StartsL.T
  object StartsL extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlinesS = InlinesS.T
  object InlinesS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Inline:
    case Text(s: Text.S)
    case Strong(s: Strong.S)
    case Em(s: Em.S)
    case Code(s: Code.S)
    case Link(text: Link.Text, href: Link.Href)
    case Cite(citekey: Cite.Citekey)

  object Inline:
    object Text:
      type S = S.T
      object S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Strong:
      type S = S.T
      object S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Em:
      type S = S.T
      object S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Code:
      type S = S.T
      object S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Link:
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Href = Href.T
      object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Cite:
      type Citekey = Citekey.T
      object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Block:
    case Heading(level: Heading.Level, text: List[Inline])
    case Para(text: List[Inline])
    case Items(ordered: Items.Ordered, items: List[List[Inline]])
    case Quote(text: List[Inline])
    case CodeBlock(text: CodeBlock.Text)

  object Block:
    object Heading:
      type Level = Level.T
      object Level extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Items:
      type Ordered = Ordered.T
      object Ordered extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object CodeBlock:
      type Text = Text.T
      object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Heading = """^(#{1,6})\s+(.*)$""".r
  private val Bullet = """^\s*[-*+]\s+(.*)$""".r
  private val Number = """^\s*\d+[.)]\s+(.*)$""".r
  private val QuoteL = """^\s*>\s?(.*)$""".r

  /** Only addresses a person can follow safely: the web, mail, or a page of this server. */
  def safeHref(h: com.histmersion.web.marks.NoteMarkdown.SafeHrefH): Option[com.histmersion.web.marks.NoteMarkdown.SafeHrefResult] =
    val t = h.trim
    Option.when(
      t.startsWith("https://") || t.startsWith("http://") || t.startsWith("mailto:") ||
        (t.startsWith("/") && !t.startsWith("//"))
    )(t)

  def blocks(text: com.histmersion.web.marks.NoteMarkdown.BlocksText): List[Block] =
    @annotation.tailrec
    def loop(lines: List[String], out: List[Block]): List[Block] = lines match
      case Nil                                   => out.reverse
      case l :: rest if l.trim.isEmpty           => loop(rest, out)
      case l :: rest if l.trim.startsWith("```") =>
        val (code, after) = rest.span(!_.trim.startsWith("```"))
        loop(after.drop(1), Block.CodeBlock(code.mkString("\n")) :: out)
      case Heading(h, t) :: rest   => loop(rest, Block.Heading(h.length, inlines(t.trim)) :: out)
      case (l @ Bullet(_)) :: rest =>
        val (more, after) = rest.span { case Bullet(_) => true; case _ => false }
        loop(after, Block.Items(false, (l :: more).collect { case Bullet(t) => inlines(t) }) :: out)
      case (l @ Number(_)) :: rest =>
        val (more, after) = rest.span { case Number(_) => true; case _ => false }
        loop(after, Block.Items(true, (l :: more).collect { case Number(t) => inlines(t) }) :: out)
      case (l @ QuoteL(_)) :: rest =>
        val (more, after) = rest.span { case QuoteL(_) => true; case _ => false }
        loop(after, Block.Quote(inlines((l :: more).collect { case QuoteL(t) => t }.mkString(" "))) :: out)
      case lines0 =>
        val (para, after) = lines0.span(l => l.trim.nonEmpty && !starts(l))
        val taken = if para.isEmpty then List(lines0.head) else para
        loop(if para.isEmpty then lines0.tail else after, Block.Para(inlines(taken.map(_.trim).mkString(" "))) :: out)
    loop(text.replace("\r\n", "\n").split("\n", -1).toList, Nil)

  private def starts(l: com.histmersion.web.marks.NoteMarkdown.StartsL): com.histmersion.web.marks.NoteMarkdown.StartsResult = l match
    case Heading(_, _) | Bullet(_) | Number(_) | QuoteL(_) => true
    case _                                                 => l.trim.startsWith("```")

  private val Token =
    """\[\[([a-z][a-z0-9_.-]{1,120})\]\]|\[([^\]]+)\]\(([^)\s]+)\)|`([^`]+)`|\*\*([^*]+)\*\*|\*([^*]+)\*|_([^_]+)_""".r

  def inlines(s: com.histmersion.web.marks.NoteMarkdown.InlinesS): List[Inline] =
    val out = List.newBuilder[Inline]
    // `var`: where the text not yet taken by a token starts
    var at = 0
    Token.findAllMatchIn(s).foreach { m =>
      if m.start > at then out += Inline.Text(s.substring(at, m.start))
      out += (if m.group(1) != null then Inline.Cite(m.group(1))
              else if m.group(2) != null then
                safeHref(m.group(3)).fold(Inline.Text(m.matched))(Inline.Link(m.group(2), _))
              else if m.group(4) != null then Inline.Code(m.group(4))
              else if m.group(5) != null then Inline.Strong(m.group(5))
              else Inline.Em(Option(m.group(6)).getOrElse(m.group(7))))
      at = m.end
    }
    if at < s.length then out += Inline.Text(s.substring(at))
    out.result()
