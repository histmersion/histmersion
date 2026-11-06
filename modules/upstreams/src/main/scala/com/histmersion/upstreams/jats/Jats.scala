package com.histmersion.upstreams.jats
object Jats:
  type CitationFieldKey = CitationFieldKey.T
  object CitationFieldKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Xml = Xml.T
  object Xml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── the tree ────────────────────────────────────────────────────────────────────────────────
  type CitationFieldsPart2 = CitationFieldsPart2.T
  object CitationFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ToMarkdownXml = _root_.com.histmersion.upstreams.jats.Jats.Xml
  val ToMarkdownXml: _root_.com.histmersion.upstreams.jats.Jats.Xml.type = _root_.com.histmersion.upstreams.jats.Jats.Xml
  type BlocksLevel = Int
  val BlocksLevel: Int.type = Int
  type BlockLevel = Int
  val BlockLevel: Int.type = Int

  sealed trait Node
  final case class Elem(name: Elem.Name, attrs: Map[Elem.AttrsKey, Elem.AttrsValue], children: Vector[Node]) extends Node:
    def kids(n: com.histmersion.upstreams.jats.Jats.Elem.Name): Vector[Elem] = children.collect { case e: Elem if e.name == n => e }
    def kid(n: com.histmersion.upstreams.jats.Jats.Elem.Name): Option[Elem] = kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name((n).value)).headOption
    def attr(n: com.histmersion.upstreams.jats.Jats.Elem.AttrsKey): Option[com.histmersion.upstreams.jats.Jats.Elem.AttrResult] = (attrs.get(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey((n).value))).map(refactorValue => _root_.com.histmersion.upstreams.jats.Jats.Elem.AttrResult((refactorValue).value))
    def elems: Vector[Elem] = children.collect { case e: Elem => e }

  object Elem:
    type AttrResult = AttrResult.T
    object AttrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AttrsKey = AttrsKey.T
    object AttrsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AttrsValue = AttrsValue.T
    object AttrsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Text(s: Text.S) extends Node

  object Text:
    type S = S.T
    object S extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The document starts — after an XML declaration, comments and a DOCTYPE — with `<article>`. */
  def isJats(xml: com.histmersion.upstreams.jats.Jats.Xml): Boolean =
    (Prolog.findPrefixOf((xml).value.take(64 * 1024)).isDefined)

  private val Prolog =
    """(?s)﻿?\s*(?:<\?.*?\?>\s*|<!--.*?-->\s*|<!DOCTYPE[^\[>]*(?:\[.*?\])?\s*>\s*)*<article[\s>/]""".r

  /** The document's root element (an empty `article` when the text has none). */
  def parse(xml: com.histmersion.upstreams.jats.Jats.Xml): Elem =
    final case class Frame(name: com.histmersion.upstreams.jats.Jats.Elem.Name, attrs: Map[com.histmersion.upstreams.jats.Jats.Elem.AttrsKey, com.histmersion.upstreams.jats.Jats.Elem.AttrsValue], kids: Vector[Node])
    // a hand-rolled reader walks the text once; the open elements are the one mutable state
    var stack = List(Frame(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("#document"), Map.empty, Vector.empty))
    def add(n: Node): Unit = stack = stack.head.copy(kids = stack.head.kids :+ n) :: stack.tail
    def close(): Unit =
      val f = stack.head
      stack = stack.tail
      add(Elem(f.name, f.attrs, f.kids))
    val n = (xml).value.length
    var i = 0 // the read position: a scanning loop, local to this call
    while i < n do
      if (xml).value.startsWith("<!--", i) then i = (after(((xml).value), ("-->"), (i + 4))).value
      else if (xml).value.startsWith("<![CDATA[", i) then
        val end = (xml).value.indexOf("]]>", i + 9)
        val stop = if end < 0 then n else end
        add(Text(_root_.com.histmersion.upstreams.jats.Jats.Text.S((xml).value.substring(i + 9, stop))))
        i = if end < 0 then n else end + 3
      else if (xml).value.startsWith("<!", i) then i = (doctypeEnd(((xml).value), (i))).value
      else if (xml).value.startsWith("<?", i) then i = (after(((xml).value), ("?>"), (i + 2))).value
      else if (xml).value.startsWith("</", i) then
        val end = (xml).value.indexOf('>', i)
        val name = (xml).value.substring(i + 2, if end < 0 then n else end).trim
        // tolerant: close up to the matching open element; a stray close tag is ignored
        if stack.exists(_.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name)) && stack.size > 1 then
          while stack.head.name != _root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name) do close()
          close()
        i = if end < 0 then n else end + 1
      else if (xml).value.charAt(i) == '<' then
        val end = tagEnd(((xml).value), (i + 1))
        val raw = (xml).value.substring(i + 1, (end).value)
        val selfClosing = raw.endsWith("/")
        val body = if selfClosing then raw.dropRight(1) else raw
        val name = body.takeWhile(c => !c.isWhitespace)
        val attrs = AttrRe
          .findAllMatchIn(body.drop(name.length))
          .map(m => m.group(1) -> entities((Option(m.group(2)).getOrElse(m.group(3)))))
          .toMap
        if name.nonEmpty then
          if selfClosing then add(Elem(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name), attrs, Vector.empty))
          else stack = Frame(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name), attrs, Vector.empty) :: stack
        i = math.min(n, (end).value + 1)
      else
        val end = (xml).value.indexOf('<', i)
        val stop = if end < 0 then n else end
        add(Text(entities(((xml).value.substring(i, stop)))))
        i = stop
    while stack.size > 1 do close()
    stack.head.kids.collectFirst { case e: Elem => e }.getOrElse(Elem(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("article"), Map.empty, Vector.empty))

  private val AttrRe = """([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r

  private def after(s: String, token: String, from: Int): Int =
    val j = (s).value.indexOf((token).value, (from).value)
    if j < 0 then ((s).value.length) else (j + (token).value.length)

  /** The `>` that ends a tag, ignoring one inside a quoted attribute value. */
  private def tagEnd(s: String, from: Int): Int =
    // a scanning index and the quote it is inside: local to this call
    var j = from
    var quote = 0.toChar
    while (j).value < (s).value.length && !(quote == 0 && (s).value.charAt((j).value) == '>') do
      val c = (s).value.charAt((j).value)
      if quote != 0 then { if c == quote then quote = 0.toChar }
      else if c == '"' || c == '\'' then quote = c
      j += 1
    ((j).value)

  /** A `<!DOCTYPE …>` with an optional `[internal subset]`. */
  private def doctypeEnd(s: String, from: Int): Int =
    // a scanning index and the bracket depth: local to this call
    var j = (from).value + 2
    var depth = 0
    while j < (s).value.length && !(depth == 0 && (s).value.charAt(j) == '>') do
      if (s).value.charAt(j) == '[' then depth += 1
      else if (s).value.charAt(j) == ']' then depth = math.max(0, depth - 1)
      j += 1
    (math.min((s).value.length, j + 1))

  private val EntityRe = """&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);""".r
  private val Named = Map("amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'", "nbsp" -> " ")

  def entities(s: String): String =
    if !(s).value.contains('&') then ((s).value)
    else
      (EntityRe.replaceAllIn(
        (s).value,
        m =>
          val e = m.group(1)
          val out =
            if e.startsWith("#x") then
              scala.util.Try(new String(Character.toChars(Integer.parseInt(e.drop(2), 16)))).toOption
            else if e.startsWith("#") then scala.util.Try(new String(Character.toChars(e.drop(1).toInt))).toOption
            else Named.get(e)
          scala.util.matching.Regex.quoteReplacement(out.getOrElse(m.matched))
      ))

  // ── rendering ───────────────────────────────────────────────────────────────────────────────

  /** The article as Markdown: title, authors, abstract(s), the body's sections, the back matter (acknowledgements,
    * notes, appendices, references) and floating figures/tables.
    */
  def toMarkdown(xml: com.histmersion.upstreams.jats.Jats.ToMarkdownXml): com.histmersion.upstreams.jats.Jats.Markdown =
    val article = parse(xml)
    val meta = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("front")).flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("article-meta")))
    val title = meta
      .flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title-group")))
      .flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("article-title")))
      .map(runText)
      .filter((_).value.nonEmpty)
      .map("# " + _)
    val authors = meta.toVector
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("contrib-group")))
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("contrib")))
      .filter(_.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey("contrib-type")).forall(_ == "author"))
      .flatMap(c => c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("name")).map(byName).orElse(c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("collab")).map(runText)))
      .filter((_).value.nonEmpty)
    val byline = Option.when(authors.nonEmpty)(authors.mkString(", "))
    val abstracts = meta.toVector
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("abstract")))
      .filterNot(a => a.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey("abstract-type")).exists(Set("graphical", "teaser", "toc")))
      .flatMap { a =>
        val heading = a.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title")).map(runText).filter((_).value.nonEmpty).getOrElse("Abstract")
        s"## $heading" +: blocks(a, (3))
      }
    val body = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("body")).toVector.flatMap(blocks(_, (2)))
    // a body that opens with running text rather than a section would read as part of the abstract
    val rule = Option.when(abstracts.nonEmpty && body.headOption.exists(!(_).value.startsWith("#")))("---")
    val back = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("back")).toVector.flatMap { b =>
      b.elems.flatMap {
        case e if e.name == "ack" =>
          val heading = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title")).map(runText).filter((_).value.nonEmpty).getOrElse("Acknowledgements")
          s"## $heading" +: blocks(e, (3))
        case e if e.name == "ref-list" => refList(e, (2))
        case e                         => block(e, (2))
      }
    }
    val floats = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("floats-group")).toVector.flatMap(blocks(_, (2)))
    (title.toVector ++ byline ++ abstracts ++ rule ++ body ++ floats ++ back)
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString("", "\n\n", "\n")

  /** The block-level Markdown of an element's children, sections as headings from `level`. */
  private def blocks(e: Elem, level: com.histmersion.upstreams.jats.Jats.BlocksLevel): Vector[String] =
    // an element's own title is rendered by whoever owns it (sec, abstract, ack, caption)
    e.children.flatMap {
      case Text(s)                                           => Option(collapse(s)).filter(_.nonEmpty).toVector
      case c: Elem if c.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title") || c.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("label") => Vector.empty
      case c: Elem                                           => block(c, level)
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

  private def block(e: Elem, level: com.histmersion.upstreams.jats.Jats.BlockLevel): Vector[String] = e.name match
    case "sec" | "app" | "boxed-text" | "glossary" | "notes" | "fn-group" | "app-group" | "sec-meta" =>
      val heading = (e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("label")).map(runText).toList ++ e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title")).map(runText).toList)
        .filter((_).value.nonEmpty)
        .mkString(" ")
      Option.when(heading.nonEmpty)("#" * math.min(6, (level).value) + " " + heading).toVector ++
        blocks(e, _root_.com.histmersion.upstreams.jats.Jats.BlocksLevel((level).value + 1))
    case "p" | "statement" | "license-p" | "attrib" =>
      // block elements nested in a paragraph (a list, a formula, a figure) come out after it
      val (inl, nested) = e.children.partition {
        case c: Elem => !BlockInP(c.name)
        case _       => true
      }
      Vector(runText(Elem(e.name, e.attrs, inl))) ++ nested.collect { case c: Elem => c }.flatMap(block(_, level))
    case "list" =>
      val ordered = e.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey("list-type")).exists(t => t == "order" || t.startsWith("alpha") || t.startsWith("roman"))
      Vector(
        e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("list-item"))
          .zipWithIndex
          .map { (it, n) =>
            val text = it.children
              .map {
                case c: Elem if c.name == "list"  => block(c, level).mkString(" ")
                case c: Elem if c.name == "label" => ""
                case c: Elem                      => runText(c)
                case Text(s)                      => s
              }
              .map(collapse)
              .filter(_.nonEmpty)
              .mkString(" ")
            (if ordered then s"${n + 1}. " else "- ") + text
          }
          .mkString("\n")
      )
    case "disp-quote" => blocks(e, level).map((_).value.linesIterator.map("> " + _).mkString("\n"))
    case "fig" | "fig-group" | "supplementary-material" | "chem-struct-wrap" =>
      val label = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("label")).map((runText(_)).value.stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("caption")).toVector.flatMap(captionText)
      if label.isEmpty && cap.isEmpty then e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("fig")).flatMap(block(_, level))
      else Vector(((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim))
    case "table-wrap" | "table-wrap-group" =>
      val label = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("label")).map((runText(_)).value.stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("caption")).toVector.flatMap(captionText)
      val head = Option
        .when(label.nonEmpty || cap.nonEmpty)((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim)
        .toVector
      head ++ e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("table")).flatMap(table) ++ e.kids("table-wrap").flatMap(block(_, level)) ++
        e.kids("table-wrap-foot").flatMap(blocks(_, level))
    case "disp-formula" => formula(e).map(f => s"$$$$$f$$$$").toVector
    case "ref-list"     => refList(e, level)
    case "def-list"     =>
      Vector(
        e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("def-item"))
          .map(d =>
            "- " + (d.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("term")).map(runText).getOrElse("") + ": " + d.kid("def").map(runText).getOrElse("")).trim
          )
          .mkString("\n")
      )
    case "fn"                                 => Vector(((runText(e)).value))
    case n if Skip(n) || n.startsWith("mml:") => Vector.empty
    case _                                    => blocks(e, level)

  private val BlockInP =
    Set("list", "disp-quote", "fig", "table-wrap", "disp-formula", "boxed-text", "def-list", "supplementary-material")

  private def captionText(c: Elem): Vector[String] =
    (c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title")).map(runText).toVector ++ c.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("p")).map(runText)).filter((_).value.nonEmpty)

  private def table(t: Elem): Vector[String] =
    def rows(e: Elem): Vector[Elem] = e.elems.flatMap(c => if c.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("tr") then Vector(c) else rows(c))
    val all = rows(t)
      .map(
        _.elems
          .filter(c => c.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("td") || c.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("th"))
          .map(c => (runText(c)).value.replace("|", "\\|"))
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
      if x.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("tex-math") then Some(x) else x.elems.iterator.flatMap(find).nextOption()
    (find(e).map(t => (collapse(((text(t)).value))).value.stripPrefix("$").stripSuffix("$").trim).filter(_.nonEmpty)).map(refactorValue3 => (refactorValue3))

  private def refList(e: Elem, level: Int, parent: Option[String] = None): Vector[String] =
    val heading = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("title")).map(runText).filter((_).value.nonEmpty).getOrElse("References")
    val shown = Option.when(!parent.contains(heading))("#" * math.min(6, (level).value) + " " + heading)
    val refs = e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("ref")).map { r =>
      val label = r.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("label")).map(runText).filter((_).value.nonEmpty).map((_).value.stripSuffix(".") + ". ").getOrElse("")
      val cites = r.elems.filter(c => c.name.endsWith("citation")).map(citation).filter((_).value.nonEmpty)
      "- " + label + cites.mkString(" ")
    }
    shown.toVector ++
      Option.when(refs.nonEmpty)(refs.mkString("\n")).toVector ++
      e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("ref-list")).flatMap(refList(_, ((level).value + 1), Some(heading)))

  /** A `mixed-citation` carries its own punctuation; an `element-citation` is bare fields, joined — a volume with its
    * issue and a page range read as one field (`522(7555):167–172`).
    */
  private def citation(c: Elem): String =
    if c.name != _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("element-citation") then ((runText(c)).value)
    else
      val fields: Vector[(com.histmersion.upstreams.jats.Jats.CitationFieldKey, com.histmersion.upstreams.jats.Jats.CitationFieldsPart2)] = c.elems.flatMap {
        case g if g.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("person-group") =>
          val names = g.elems
            .flatMap {
              case n if n.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("name") => Some(personName(n))
              case n if n.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("etal") => Some("et al.")
              case n                     => Some(runText(n))
            }
            .filter(_.nonEmpty)
          Option.when(names.nonEmpty)({ val refactorTuple = "person-group" -> names.mkString(", "); (_root_.com.histmersion.upstreams.jats.Jats.CitationFieldKey(refactorTuple._1), _root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart2(refactorTuple._2)) })
        case p if p.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("pub-id") =>
          Option.when(p.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey("pub-id-type")).contains("doi"))({ val refactorTuple2 = "doi" -> ("doi:" + runText(p)); (_root_.com.histmersion.upstreams.jats.Jats.CitationFieldKey(refactorTuple2._1), _root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart2(refactorTuple2._2)) })
        case x => Some(x.name -> runText(x))
      }
      val merged = fields.foldLeft(Vector.empty[(String, String)]) { (acc, f) =>
        (f, acc.lastOption) match
          case ((refactorPattern, t), Some(("volume", v))) if (refactorPattern.value == "issue") => acc.init :+ ("volume" -> s"$v($t)")
          case ((refactorPattern2, t), Some(("volume", v))) if (refactorPattern2.value == "fpage") => acc.init :+ ("fpage" -> s"$v:$t")
          case ((refactorPattern3, t), Some(("fpage", p))) if (refactorPattern3.value == "lpage")  => acc.init :+ ("lpage" -> s"$p\u2013$t")
          case _                                   => acc :+ { val refactorTuple3 = f; ((refactorTuple3._1).value, (refactorTuple3._2).value) }
      }
      merged.map(_._2.trim.stripSuffix(".")).filter(_.nonEmpty) match
        case Vector() => ("")
        case parts    => (parts.mkString("", ". ", "."))

  /** `Given Surname`, for the byline. */
  private def byName(n: Elem): String =
    ((n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("given-names")).map(runText).toList ++ n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("surname")).map(runText).toList ++
      n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("suffix")).map(runText).toList).filter((_).value.nonEmpty).mkString(" "))

  /** `Surname Given`, as references write it. */
  private def personName(n: Elem): String =
    ((n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("surname")).map(runText).toList ++ n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("given-names")).map(runText).toList ++
      n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("suffix")).map(runText).toList).filter((_).value.nonEmpty).mkString(" "))

  /** An element's running text as one Markdown line. */
  def runText(e: Elem): com.histmersion.upstreams.jats.Jats.Text.S = _root_.com.histmersion.upstreams.jats.Jats.Text.S((collapse(((inlineRaw(e)).value))).value)

  private def inlineRaw(e: Elem): String =
    e.children.map {
      case Text(s) => s
      case c: Elem =>
        c.name match
          case "italic" | "em"                               => wrap(("*"), ((inlineRaw(c)).value))
          case "bold" | "strong"                             => wrap(("**"), ((inlineRaw(c)).value))
          case "xref" if c.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey("ref-type")).contains("bibr") =>
            val t = collapse(((inlineRaw(c)).value))
            if (t).value.isEmpty then "" else s"[$t]"
          case "inline-formula"                                        => formula(c).map(f => s"$$$f$$").getOrElse("")
          case "break"                                                 => " "
          case "fn" | "table-wrap" | "fig" | "disp-formula" | "list"   => " " + collapse(((inlineRaw(c)).value)) + " "
          case n if Skip(n) || n.startsWith("mml:") || n == "tex-math" => ""
          case _                                                       => inlineRaw(c)
    }.mkString

  private def wrap(mark: String, s: String): String =
    val t = (s).value.trim
    if t.isEmpty then ((s).value)
    else
      ((if (s).value.headOption.exists(_.isWhitespace) then " " else "") + mark + t + mark +
        (if (s).value.lastOption.exists(_.isWhitespace) then " " else ""))

  private def text(e: Elem): String = (e.children.map {
    case Text(s) => s
    case c: Elem => text(c)
  }.mkString)

  private def collapse(s: String): String = ((s).value.replaceAll("[\\s ]+", " ").trim)
