package com.histmersion.web.marks

/** The little Markdown a note is written in, read for its preview: headings (`#`), paragraphs, lists (`-`, `*`, `1.`),
  * quotes (`>`), code blocks (```), and in a line `**strong**`, `*emphasis*`, `` `code` ``, `[text](https://…)` and
  * `[[citekey]]`. Anything else is plain text. Pure: the page builds the elements from this, never HTML from the text.
  */
object NoteMarkdown:

  enum Inline:
    case Text(s: String)
    case Strong(s: String)
    case Em(s: String)
    case Code(s: String)
    case Link(text: String, href: String)
    case Cite(citekey: String)

  enum Block:
    case Heading(level: Int, text: List[Inline])
    case Para(text: List[Inline])
    case Items(ordered: Boolean, items: List[List[Inline]])
    case Quote(text: List[Inline])
    case CodeBlock(text: String)

  private val Heading = """^(#{1,6})\s+(.*)$""".r
  private val Bullet = """^\s*[-*+]\s+(.*)$""".r
  private val Number = """^\s*\d+[.)]\s+(.*)$""".r
  private val QuoteL = """^\s*>\s?(.*)$""".r

  /** Only addresses a person can follow safely: the web, mail, or a page of this server. */
  def safeHref(h: String): Option[String] =
    val t = h.trim
    Option.when(
      t.startsWith("https://") || t.startsWith("http://") || t.startsWith("mailto:") ||
        (t.startsWith("/") && !t.startsWith("//"))
    )(t)

  def blocks(text: String): List[Block] =
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

  private def starts(l: String): Boolean = l match
    case Heading(_, _) | Bullet(_) | Number(_) | QuoteL(_) => true
    case _                                                 => l.trim.startsWith("```")

  private val Token =
    """\[\[([a-z][a-z0-9_.-]{1,120})\]\]|\[([^\]]+)\]\(([^)\s]+)\)|`([^`]+)`|\*\*([^*]+)\*\*|\*([^*]+)\*|_([^_]+)_""".r

  def inlines(s: String): List[Inline] =
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
