package com.histmersion
package upstreams.jats

import io.github.iltotore.iron.constraint.any.Pure
object Jats:
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]
  type Xml = Xml.T
  object Xml extends Newtype[String, Pure]

  // ── the tree ────────────────────────────────────────────────────────────────────────────────
  sealed trait Node
  final case class Elem(name: Elem.Name, attrs: Map[String, String], children: Vector[Node]) extends Node:
    def kids(n: Elem.Name): Vector[Elem] = children.collect { case e: Elem if e.name == n => e }
    def kid(n: Elem.Name): Option[Elem] = kids(Elem.Name((n).value)).headOption
    def attr(n: String): Option[String] = attrs.get(n)
    def elems: Vector[Elem] = children.collect { case e: Elem => e }

  object Elem:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
  final case class Text(s: String) extends Node

  /** The document starts — after an XML declaration, comments and a DOCTYPE — with `<article>`. */
  def isJats(xml: Xml): Boolean =
    (Prolog.findPrefixOf((xml).value.take(64 * 1024)).isDefined)

  private val Prolog =
    """(?s)﻿?\s*(?:<\?.*?\?>\s*|<!--.*?-->\s*|<!DOCTYPE[^\[>]*(?:\[.*?\])?\s*>\s*)*<article[\s>/]""".r

  /** The document's root element (an empty `article` when the text has none). */
  def parse(xml: Xml): Elem =
    final case class Frame(
        name: com.histmersion.upstreams.jats.Jats.Elem.Name,
        attrs: Map[String, String],
        kids: Vector[Node]
    )
    // a hand-rolled reader walks the text once; the open elements are the one mutable state
    var stack = List(Frame(Elem.Name("#document"), Map.empty, Vector.empty))
    def add(n: Node): Unit = stack = stack.head.copy(kids = stack.head.kids :+ n) :: stack.tail
    def close(): Unit =
      val f = stack.head
      stack = stack.tail
      add(Elem(f.name, f.attrs, f.kids))
    val n = (xml).value.length
    var i = 0 // the read position: a scanning loop, local to this call
    while i < n do
      if (xml).value.startsWith("<!--", i) then i = (after(((xml).value), ("-->"), (i + 4)))
      else if (xml).value.startsWith("<![CDATA[", i) then
        val end = (xml).value.indexOf("]]>", i + 9)
        val stop = if end < 0 then n else end
        add(Text((xml).value.substring(i + 9, stop)))
        i = if end < 0 then n else end + 3
      else if (xml).value.startsWith("<!", i) then i = (doctypeEnd(((xml).value), (i)))
      else if (xml).value.startsWith("<?", i) then i = (after(((xml).value), ("?>"), (i + 2)))
      else if (xml).value.startsWith("</", i) then
        val end = (xml).value.indexOf('>', i)
        val name = (xml).value.substring(i + 2, if end < 0 then n else end).trim
        // tolerant: close up to the matching open element; a stray close tag is ignored
        if stack.exists(_.name == Elem.Name(name)) && stack.size > 1 then
          while stack.head.name != Elem.Name(name) do close()
          close()
        i = if end < 0 then n else end + 1
      else if (xml).value.charAt(i) == '<' then
        val end = tagEnd(((xml).value), (i + 1))
        val raw = (xml).value.substring(i + 1, (end))
        val selfClosing = raw.endsWith("/")
        val body = if selfClosing then raw.dropRight(1) else raw
        val name = body.takeWhile(c => !c.isWhitespace)
        val attrs = AttrRe
          .findAllMatchIn(body.drop(name.length))
          .map(m => m.group(1) -> entities((Option(m.group(2)).getOrElse(m.group(3)))))
          .toMap
        if name.nonEmpty then
          if selfClosing then add(Elem(Elem.Name(name), attrs, Vector.empty))
          else stack = Frame(Elem.Name(name), attrs, Vector.empty) :: stack
        i = math.min(n, (end) + 1)
      else
        val end = (xml).value.indexOf('<', i)
        val stop = if end < 0 then n else end
        add(Text(entities(((xml).value.substring(i, stop)))))
        i = stop
    while stack.size > 1 do close()
    stack.head.kids.collectFirst { case e: Elem => e }.getOrElse(Elem(Elem.Name("article"), Map.empty, Vector.empty))

  private val AttrRe = """([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r

  private def after(s: String, token: String, from: Int): Int =
    val j = s.indexOf(token, from)
    if j < 0 then s.length else j + token.length

  /** The `>` that ends a tag, ignoring one inside a quoted attribute value. */
  private def tagEnd(s: String, from: Int): Int =
    // a scanning index and the quote it is inside: local to this call
    var j = from
    var quote = 0.toChar
    while j < s.length && !(quote == 0 && s.charAt(j) == '>') do
      val c = s.charAt(j)
      if quote != 0 then { if c == quote then quote = 0.toChar }
      else if c == '"' || c == '\'' then quote = c
      j += 1
    ((j))

  /** A `<!DOCTYPE …>` with an optional `[internal subset]`. */
  private def doctypeEnd(s: String, from: Int): Int =
    // a scanning index and the bracket depth: local to this call
    var j = (from) + 2
    var depth = 0
    while j < s.length && !(depth == 0 && s.charAt(j) == '>') do
      if (s).charAt(j) == '[' then depth += 1
      else if (s).charAt(j) == ']' then depth = math.max(0, depth - 1)
      j += 1
    (math.min((s).length, j + 1))

  private val EntityRe = """&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);""".r
  private val Named = Map("amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'", "nbsp" -> " ")

  def entities(s: String): String =
    if !(s).contains('&') then ((s))
    else
      (EntityRe.replaceAllIn(
        (s),
        m =>
          val e = m.group(1)
          val out =
            if e.startsWith("#x") then
              scala.util.Try(new String(Character.toChars(Integer.parseInt(e.drop(2), 16)))).toOption
            else if e.startsWith("#") then scala.util.Try(new String(Character.toChars(e.drop(1).toInt))).toOption
            else Named.get(e)
          scala.util.matching.Regex.quoteReplacement(out.getOrElse(m.matched))
      )
    )

  // ── rendering ───────────────────────────────────────────────────────────────────────────────

  /** The article as Markdown: title, authors, abstract(s), the body's sections, the back matter (acknowledgements,
    * notes, appendices, references) and floating figures/tables.
    */
  def toMarkdown(xml: Xml): Markdown =
    val article = parse(xml)
    val meta = article.kid(Elem.Name("front")).flatMap(_.kid(Elem.Name("article-meta")))
    val title = meta
      .flatMap(_.kid(Elem.Name("title-group")))
      .flatMap(_.kid(Elem.Name("article-title")))
      .map(runText)
      .filter(_.nonEmpty)
      .map("# " + _)
    val authors = meta.toVector
      .flatMap(_.kids(Elem.Name("contrib-group")))
      .flatMap(_.kids(Elem.Name("contrib")))
      .filter(_.attr("contrib-type").forall(_ == "author"))
      .flatMap(c => c.kid(Elem.Name("name")).map(byName).orElse(c.kid(Elem.Name("collab")).map(runText)))
      .filter((_).nonEmpty)
    val byline = Option.when(authors.nonEmpty)(authors.mkString(", "))
    val abstracts = meta.toVector
      .flatMap(_.kids(Elem.Name("abstract")))
      .filterNot(a => a.attr("abstract-type").exists(Set("graphical", "teaser", "toc")))
      .flatMap { a =>
        val heading = a.kid(Elem.Name("title")).map(runText).filter(_.nonEmpty).getOrElse("Abstract")
        s"## $heading" +: blocks(a, (3))
      }
    val body = article.kid(Elem.Name("body")).toVector.flatMap(blocks(_, (2)))
    // a body that opens with running text rather than a section would read as part of the abstract
    val rule = Option.when(abstracts.nonEmpty && body.headOption.exists(!(_).startsWith("#")))("---")
    val back = article.kid(Elem.Name("back")).toVector.flatMap { b =>
      b.elems.flatMap {
        case e if e.name == Elem.Name("ack") =>
          val heading = e.kid(Elem.Name("title")).map(runText).filter(_.nonEmpty).getOrElse("Acknowledgements")
          s"## $heading" +: blocks(e, (3))
        case e if e.name == Elem.Name("ref-list") => refList(e, (2))
        case e                                    => block(e, (2))
      }
    }
    val floats = article.kid(Elem.Name("floats-group")).toVector.flatMap(blocks(_, (2)))
    Markdown(
      (title.toVector ++ byline ++ abstracts ++ rule ++ body ++ floats ++ back)
        .map(_.trim)
        .filter(_.nonEmpty)
        .mkString("", "\n\n", "\n")
    )

  /** The block-level Markdown of an element's children, sections as headings from `level`. */
  private def blocks(e: Elem, level: Int): Vector[String] =
    // an element's own title is rendered by whoever owns it (sec, abstract, ack, caption)
    e.children.flatMap {
      case Text(s) => Option(collapse(s)).filter(_.nonEmpty).toVector
      case c: Elem if c.name == Elem.Name("title") || c.name == Elem.Name("label") => Vector.empty
      case c: Elem                                                                 => block(c, level)
    }

  private val Skip = Set(
    "graphic",
    "media",
    "alternatives",
    "object-id",
    "permissions",
    "self-uri",
    "inline-graphic",
    "x",
    "related-article",
    "kwd-group",
    "funding-group",
    "custom-meta-group",
    "counts",
    "history",
    "article-categories"
  )

  private def block(e: Elem, level: Int): Vector[String] = e.name.value match
    case "sec" | "app" | "boxed-text" | "glossary" | "notes" | "fn-group" | "app-group" | "sec-meta" =>
      val heading = (e.kid(Elem.Name("label")).map(runText).toList ++ e.kid(Elem.Name("title")).map(runText).toList)
        .filter(_.nonEmpty)
        .mkString(" ")
      Option.when(heading.nonEmpty)("#" * math.min(6, (level)) + " " + heading).toVector ++
        blocks(e, level + 1)
    case "p" | "statement" | "license-p" | "attrib" =>
      // block elements nested in a paragraph (a list, a formula, a figure) come out after it
      val (inl, nested) = e.children.partition {
        case c: Elem => !BlockInP(c.name.value)
        case _       => true
      }
      Vector(runText(Elem(e.name, e.attrs, inl))) ++ nested.collect { case c: Elem => c }.flatMap(block(_, level))
    case "list" =>
      val ordered = e.attr("list-type").exists(t => t == "order" || t.startsWith("alpha") || t.startsWith("roman"))
      Vector(
        e.kids(Elem.Name("list-item"))
          .zipWithIndex
          .map { (it, n) =>
            val text = it.children
              .map {
                case c: Elem if c.name == Elem.Name("list")  => block(c, level).mkString(" ")
                case c: Elem if c.name == Elem.Name("label") => ""
                case c: Elem                                 => runText(c)
                case Text(s)                                 => s
              }
              .map(collapse)
              .filter(_.nonEmpty)
              .mkString(" ")
            (if ordered then s"${n + 1}. " else "- ") + text
          }
          .mkString("\n")
      )
    case "disp-quote" => blocks(e, level).map((_).linesIterator.map("> " + _).mkString("\n"))
    case "fig" | "fig-group" | "supplementary-material" | "chem-struct-wrap" =>
      val label = e.kid(Elem.Name("label")).map(runText(_).stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(Elem.Name("caption")).toVector.flatMap(captionText)
      if label.isEmpty && cap.isEmpty then e.kids(Elem.Name("fig")).flatMap(block(_, level))
      else Vector(((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim))
    case "table-wrap" | "table-wrap-group" =>
      val label = e.kid(Elem.Name("label")).map(runText(_).stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(Elem.Name("caption")).toVector.flatMap(captionText)
      val head = Option
        .when(label.nonEmpty || cap.nonEmpty)((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim)
        .toVector
      head ++ e.kids(Elem.Name("table")).flatMap(table) ++ e.kids(Elem.Name("table-wrap")).flatMap(block(_, level)) ++
        e.kids(Elem.Name("table-wrap-foot")).flatMap(blocks(_, level))
    case "disp-formula" => formula(e).map(f => s"$$$$$f$$$$").toVector
    case "ref-list"     => refList(e, level)
    case "def-list"     =>
      Vector(
        e.kids(Elem.Name("def-item"))
          .map(d =>
            "- " + (d.kid(Elem.Name("term")).map(runText).getOrElse("") + ": " + d
              .kid(Elem.Name("def"))
              .map(runText)
              .getOrElse("")).trim
          )
          .mkString("\n")
      )
    case "fn"                                 => Vector(runText(e))
    case n if Skip(n) || n.startsWith("mml:") => Vector.empty
    case _                                    => blocks(e, level)

  private val BlockInP =
    Set("list", "disp-quote", "fig", "table-wrap", "disp-formula", "boxed-text", "def-list", "supplementary-material")

  private def captionText(c: Elem): Vector[String] =
    (c.kid(Elem.Name("title")).map(runText).toVector ++ c.kids(Elem.Name("p")).map(runText)).filter(_.nonEmpty)

  private def table(t: Elem): Vector[String] =
    def rows(e: Elem): Vector[Elem] = e.elems.flatMap(c => if c.name == Elem.Name("tr") then Vector(c) else rows(c))
    val all = rows(t)
      .map(
        _.elems
          .filter(c => c.name == Elem.Name("td") || c.name == Elem.Name("th"))
          .map(c => runText(c).replace("|", "\\|"))
      )
      .filter(_.nonEmpty)
    if all.isEmpty then Vector.empty
    else
      val width = all.map(_.size).max
      val pad = all.map(r => r ++ Vector.fill(width - r.size)(""))
      val line = (r: Vector[String]) => r.mkString("| ", " | ", " |")
      Vector(((line(pad.head) +: line(Vector.fill(width)("---")) +: pad.tail.map(line)).mkString("\n")))

  private def formula(e: Elem): Option[String] =
    def find(x: Elem): Option[Elem] =
      if x.name == Elem.Name("tex-math") then Some(x) else x.elems.iterator.flatMap(find).nextOption()
    find(e).map(t => collapse(text(t)).stripPrefix("$").stripSuffix("$").trim).filter(_.nonEmpty)

  private def refList(e: Elem, level: Int, parent: Option[String] = None): Vector[String] =
    val heading = e.kid(Elem.Name("title")).map(runText).filter(_.nonEmpty).getOrElse("References")
    val shown = Option.when(!parent.contains(heading))("#" * math.min(6, (level)) + " " + heading)
    val refs = e.kids(Elem.Name("ref")).map { r =>
      val label = r.kid(Elem.Name("label")).map(runText).filter(_.nonEmpty).map(_.stripSuffix(".") + ". ").getOrElse("")
      val cites = r.elems.filter(c => c.name.value.endsWith("citation")).map(citation).filter((_).nonEmpty)
      "- " + label + cites.mkString(" ")
    }
    shown.toVector ++
      Option.when(refs.nonEmpty)(refs.mkString("\n")).toVector ++
      e.kids(Elem.Name("ref-list")).flatMap(refList(_, level + 1, Some(heading)))

  /** A `mixed-citation` carries its own punctuation; an `element-citation` is bare fields, joined — a volume with its
    * issue and a page range read as one field (`522(7555):167–172`).
    */
  private def citation(c: Elem): String =
    if c.name != Elem.Name("element-citation") then runText(c)
    else
      val fields: Vector[(String, String)] = c.elems.flatMap {
        case g if g.name == Elem.Name("person-group") =>
          val names = g.elems
            .flatMap {
              case n if n.name == Elem.Name("name") => Some(personName(n))
              case n if n.name == Elem.Name("etal") => Some("et al.")
              case n                                => Some(runText(n))
            }
            .filter(_.nonEmpty)
          Option.when(names.nonEmpty)("person-group" -> names.mkString(", "))
        case p if p.name == Elem.Name("pub-id") =>
          Option.when(p.attr("pub-id-type").contains("doi"))("doi" -> ("doi:" + runText(p)))
        case x => Some(x.name.value -> runText(x))
      }
      val merged = fields.foldLeft(Vector.empty[(String, String)]) { (acc, f) =>
        (f, acc.lastOption) match
          case (("issue", t), Some(("volume", v))) => acc.init :+ ("volume" -> s"$v($t)")
          case (("fpage", t), Some(("volume", v))) => acc.init :+ ("fpage" -> s"$v:$t")
          case (("lpage", t), Some(("fpage", p)))  => acc.init :+ ("lpage" -> s"$p\u2013$t")
          case _                                   => acc :+ f
      }
      merged.map(_._2.trim.stripSuffix(".")).filter(_.nonEmpty) match
        case Vector() => ("")
        case parts    => (parts.mkString("", ". ", "."))

  /** `Given Surname`, for the byline. */
  private def byName(n: Elem): String =
    ((n.kid(Elem.Name("given-names")).map(runText).toList ++ n.kid(Elem.Name("surname")).map(runText).toList ++
      n.kid(Elem.Name("suffix")).map(runText).toList).filter(_.nonEmpty).mkString(" "))

  /** `Surname Given`, as references write it. */
  private def personName(n: Elem): String =
    ((n.kid(Elem.Name("surname")).map(runText).toList ++ n.kid(Elem.Name("given-names")).map(runText).toList ++
      n.kid(Elem.Name("suffix")).map(runText).toList).filter(_.nonEmpty).mkString(" "))

  /** An element's running text as one Markdown line. */
  def runText(e: Elem): String = collapse(inlineRaw(e))

  private def inlineRaw(e: Elem): String =
    e.children.map {
      case Text(s) => s
      case c: Elem =>
        c.name.value match
          case "italic" | "em"                               => wrap(("*"), ((inlineRaw(c))))
          case "bold" | "strong"                             => wrap(("**"), ((inlineRaw(c))))
          case "xref" if c.attr("ref-type").contains("bibr") =>
            val t = collapse(((inlineRaw(c))))
            if t.isEmpty then "" else s"[$t]"
          case "inline-formula"                                        => formula(c).map(f => s"$$$f$$").getOrElse("")
          case "break"                                                 => " "
          case "fn" | "table-wrap" | "fig" | "disp-formula" | "list"   => " " + collapse(((inlineRaw(c)))) + " "
          case n if Skip(n) || n.startsWith("mml:") || n == "tex-math" => ""
          case _                                                       => inlineRaw(c)
    }.mkString

  private def wrap(mark: String, s: String): String =
    val t = (s).trim
    if t.isEmpty then ((s))
    else
      ((if (s).headOption.exists(_.isWhitespace) then " " else "") + mark + t + mark +
        (if (s).lastOption.exists(_.isWhitespace) then " " else "")
    )

  private def text(e: Elem): String = (e.children.map {
    case Text(s) => s
    case c: Elem => text(c)
  }.mkString)

  private def collapse(s: String): String = ((s).replaceAll("[\\s ]+", " ").trim)
