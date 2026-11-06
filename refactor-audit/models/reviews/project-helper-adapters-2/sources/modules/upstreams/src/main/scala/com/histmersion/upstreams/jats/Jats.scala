package com.histmersion.upstreams.jats
object Jats:

  // ── the tree ────────────────────────────────────────────────────────────────────────────────
  type CitationFieldsPart1 = CitationFieldsPart1.T
  object CitationFieldsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitationFieldsPart2 = CitationFieldsPart2.T
  object CitationFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsJatsResult = IsJatsResult.T
  object IsJatsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsJatsXml = IsJatsXml.T
  object IsJatsXml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseXml = ParseXml.T
  object ParseXml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterResult = AfterResult.T
  object AfterResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterS = AfterS.T
  object AfterS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterToken = AfterToken.T
  object AfterToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AfterFrom = AfterFrom.T
  object AfterFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagEndResult = TagEndResult.T
  object TagEndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagEndS = TagEndS.T
  object TagEndS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagEndFrom = TagEndFrom.T
  object TagEndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoctypeEndResult = DoctypeEndResult.T
  object DoctypeEndResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoctypeEndS = DoctypeEndS.T
  object DoctypeEndS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoctypeEndFrom = DoctypeEndFrom.T
  object DoctypeEndFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntitiesResult = EntitiesResult.T
  object EntitiesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntitiesS = EntitiesS.T
  object EntitiesS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToMarkdownResult = ToMarkdownResult.T
  object ToMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToMarkdownXml = _root_.com.histmersion.upstreams.jats.Jats.ParseXml
  val ToMarkdownXml: _root_.com.histmersion.upstreams.jats.Jats.ParseXml.type = _root_.com.histmersion.upstreams.jats.Jats.ParseXml
  type BlocksResult = BlocksResult.T
  object BlocksResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlocksLevel = _root_.com.histmersion.upstreams.jats.Jats.RefListLevel
  val BlocksLevel: _root_.com.histmersion.upstreams.jats.Jats.RefListLevel.type = _root_.com.histmersion.upstreams.jats.Jats.RefListLevel
  type BlockResult = BlockResult.T
  object BlockResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockLevel = _root_.com.histmersion.upstreams.jats.Jats.RefListLevel
  val BlockLevel: _root_.com.histmersion.upstreams.jats.Jats.RefListLevel.type = _root_.com.histmersion.upstreams.jats.Jats.RefListLevel
  type CaptionTextResult = CaptionTextResult.T
  object CaptionTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableResult = TableResult.T
  object TableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormulaResult = FormulaResult.T
  object FormulaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefListResult = RefListResult.T
  object RefListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefListLevel = RefListLevel.T
  object RefListLevel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefListParent = RefListParent.T
  object RefListParent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitationResult = CitationResult.T
  object CitationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByNameResult = ByNameResult.T
  object ByNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonNameResult = PersonNameResult.T
  object PersonNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunTextResult = RunTextResult.T
  object RunTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlineRawResult = InlineRawResult.T
  object InlineRawResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrapResult = WrapResult.T
  object WrapResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrapMark = WrapMark.T
  object WrapMark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WrapS = WrapS.T
  object WrapS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResult = TextResult.T
  object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollapseResult = CollapseResult.T
  object CollapseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollapseS = CollapseS.T
  object CollapseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  sealed trait Node
  final case class Elem(name: Elem.Name, attrs: Map[Elem.AttrsKey, Elem.AttrsValue], children: Vector[Node]) extends Node:
    def kids(n: com.histmersion.upstreams.jats.Jats.Elem.KidsN): Vector[Elem] = children.collect { case e: Elem if e.name == n => e }
    def kid(n: com.histmersion.upstreams.jats.Jats.Elem.KidN): Option[Elem] = kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN((n).value)).headOption
    def attr(n: com.histmersion.upstreams.jats.Jats.Elem.AttrN): Option[com.histmersion.upstreams.jats.Jats.Elem.AttrResult] = (attrs.get(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrsKey((n).value))).map(refactorValue => _root_.com.histmersion.upstreams.jats.Jats.Elem.AttrResult((refactorValue).value))
    def elems: Vector[Elem] = children.collect { case e: Elem => e }

  object Elem:
    type KidsN = KidsN.T
    object KidsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KidN = KidN.T
    object KidN extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AttrResult = AttrResult.T
    object AttrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AttrN = AttrN.T
    object AttrN extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  def isJats(xml: com.histmersion.upstreams.jats.Jats.IsJatsXml): com.histmersion.upstreams.jats.Jats.IsJatsResult =
    _root_.com.histmersion.upstreams.jats.Jats.IsJatsResult(Prolog.findPrefixOf((xml).value.take(64 * 1024)).isDefined)

  private val Prolog =
    """(?s)﻿?\s*(?:<\?.*?\?>\s*|<!--.*?-->\s*|<!DOCTYPE[^\[>]*(?:\[.*?\])?\s*>\s*)*<article[\s>/]""".r

  /** The document's root element (an empty `article` when the text has none). */
  def parse(xml: com.histmersion.upstreams.jats.Jats.ParseXml): Elem =
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
      if (xml).value.startsWith("<!--", i) then i = (after(_root_.com.histmersion.upstreams.jats.Jats.AfterS((xml).value), _root_.com.histmersion.upstreams.jats.Jats.AfterToken("-->"), _root_.com.histmersion.upstreams.jats.Jats.AfterFrom(i + 4))).value
      else if (xml).value.startsWith("<![CDATA[", i) then
        val end = (xml).value.indexOf("]]>", i + 9)
        val stop = if end < 0 then n else end
        add(Text(_root_.com.histmersion.upstreams.jats.Jats.Text.S((xml).value.substring(i + 9, stop))))
        i = if end < 0 then n else end + 3
      else if (xml).value.startsWith("<!", i) then i = (doctypeEnd(_root_.com.histmersion.upstreams.jats.Jats.DoctypeEndS((xml).value), _root_.com.histmersion.upstreams.jats.Jats.DoctypeEndFrom(i))).value
      else if (xml).value.startsWith("<?", i) then i = (after(_root_.com.histmersion.upstreams.jats.Jats.AfterS((xml).value), _root_.com.histmersion.upstreams.jats.Jats.AfterToken("?>"), _root_.com.histmersion.upstreams.jats.Jats.AfterFrom(i + 2))).value
      else if (xml).value.startsWith("</", i) then
        val end = (xml).value.indexOf('>', i)
        val name = (xml).value.substring(i + 2, if end < 0 then n else end).trim
        // tolerant: close up to the matching open element; a stray close tag is ignored
        if stack.exists(_.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name)) && stack.size > 1 then
          while stack.head.name != _root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name) do close()
          close()
        i = if end < 0 then n else end + 1
      else if (xml).value.charAt(i) == '<' then
        val end = tagEnd(_root_.com.histmersion.upstreams.jats.Jats.TagEndS((xml).value), _root_.com.histmersion.upstreams.jats.Jats.TagEndFrom(i + 1))
        val raw = (xml).value.substring(i + 1, (end).value)
        val selfClosing = raw.endsWith("/")
        val body = if selfClosing then raw.dropRight(1) else raw
        val name = body.takeWhile(c => !c.isWhitespace)
        val attrs = AttrRe
          .findAllMatchIn(body.drop(name.length))
          .map(m => m.group(1) -> entities(_root_.com.histmersion.upstreams.jats.Jats.EntitiesS(Option(m.group(2)).getOrElse(m.group(3)))))
          .toMap
        if name.nonEmpty then
          if selfClosing then add(Elem(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name), attrs, Vector.empty))
          else stack = Frame(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name(name), attrs, Vector.empty) :: stack
        i = math.min(n, (end).value + 1)
      else
        val end = (xml).value.indexOf('<', i)
        val stop = if end < 0 then n else end
        add(Text(entities(_root_.com.histmersion.upstreams.jats.Jats.EntitiesS((xml).value.substring(i, stop)))))
        i = stop
    while stack.size > 1 do close()
    stack.head.kids.collectFirst { case e: Elem => e }.getOrElse(Elem(_root_.com.histmersion.upstreams.jats.Jats.Elem.Name("article"), Map.empty, Vector.empty))

  private val AttrRe = """([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""".r

  private def after(s: com.histmersion.upstreams.jats.Jats.AfterS, token: com.histmersion.upstreams.jats.Jats.AfterToken, from: com.histmersion.upstreams.jats.Jats.AfterFrom): com.histmersion.upstreams.jats.Jats.AfterResult =
    val j = (s).value.indexOf((token).value, (from).value)
    if j < 0 then _root_.com.histmersion.upstreams.jats.Jats.AfterResult((s).value.length) else _root_.com.histmersion.upstreams.jats.Jats.AfterResult(j + (token).value.length)

  /** The `>` that ends a tag, ignoring one inside a quoted attribute value. */
  private def tagEnd(s: com.histmersion.upstreams.jats.Jats.TagEndS, from: com.histmersion.upstreams.jats.Jats.TagEndFrom): com.histmersion.upstreams.jats.Jats.TagEndResult =
    // a scanning index and the quote it is inside: local to this call
    var j = from
    var quote = 0.toChar
    while (j).value < (s).value.length && !(quote == 0 && (s).value.charAt((j).value) == '>') do
      val c = (s).value.charAt((j).value)
      if quote != 0 then { if c == quote then quote = 0.toChar }
      else if c == '"' || c == '\'' then quote = c
      j += 1
    _root_.com.histmersion.upstreams.jats.Jats.TagEndResult((j).value)

  /** A `<!DOCTYPE …>` with an optional `[internal subset]`. */
  private def doctypeEnd(s: com.histmersion.upstreams.jats.Jats.DoctypeEndS, from: com.histmersion.upstreams.jats.Jats.DoctypeEndFrom): com.histmersion.upstreams.jats.Jats.DoctypeEndResult =
    // a scanning index and the bracket depth: local to this call
    var j = (from).value + 2
    var depth = 0
    while j < (s).value.length && !(depth == 0 && (s).value.charAt(j) == '>') do
      if (s).value.charAt(j) == '[' then depth += 1
      else if (s).value.charAt(j) == ']' then depth = math.max(0, depth - 1)
      j += 1
    _root_.com.histmersion.upstreams.jats.Jats.DoctypeEndResult(math.min((s).value.length, j + 1))

  private val EntityRe = """&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]*);""".r
  private val Named = Map("amp" -> "&", "lt" -> "<", "gt" -> ">", "quot" -> "\"", "apos" -> "'", "nbsp" -> " ")

  def entities(s: com.histmersion.upstreams.jats.Jats.EntitiesS): com.histmersion.upstreams.jats.Jats.EntitiesResult =
    if !(s).value.contains('&') then _root_.com.histmersion.upstreams.jats.Jats.EntitiesResult((s).value)
    else
      _root_.com.histmersion.upstreams.jats.Jats.EntitiesResult(EntityRe.replaceAllIn(
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
  def toMarkdown(xml: com.histmersion.upstreams.jats.Jats.ToMarkdownXml): com.histmersion.upstreams.jats.Jats.ToMarkdownResult =
    val article = parse(xml)
    val meta = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("front")).flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("article-meta")))
    val title = meta
      .flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title-group")))
      .flatMap(_.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("article-title")))
      .map(runText)
      .filter((_).value.nonEmpty)
      .map("# " + _)
    val authors = meta.toVector
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("contrib-group")))
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("contrib")))
      .filter(_.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrN("contrib-type")).forall(_ == "author"))
      .flatMap(c => c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("name")).map(byName).orElse(c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("collab")).map(runText)))
      .filter((_).value.nonEmpty)
    val byline = Option.when(authors.nonEmpty)(authors.mkString(", "))
    val abstracts = meta.toVector
      .flatMap(_.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("abstract")))
      .filterNot(a => a.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrN("abstract-type")).exists(Set("graphical", "teaser", "toc")))
      .flatMap { a =>
        val heading = a.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title")).map(runText).filter((_).value.nonEmpty).getOrElse("Abstract")
        s"## $heading" +: blocks(a, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(3))
      }
    val body = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("body")).toVector.flatMap(blocks(_, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(2)))
    // a body that opens with running text rather than a section would read as part of the abstract
    val rule = Option.when(abstracts.nonEmpty && body.headOption.exists(!(_).value.startsWith("#")))("---")
    val back = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("back")).toVector.flatMap { b =>
      b.elems.flatMap {
        case e if e.name == "ack" =>
          val heading = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title")).map(runText).filter((_).value.nonEmpty).getOrElse("Acknowledgements")
          s"## $heading" +: blocks(e, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(3))
        case e if e.name == "ref-list" => refList(e, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(2))
        case e                         => block(e, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(2))
      }
    }
    val floats = article.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("floats-group")).toVector.flatMap(blocks(_, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel(2)))
    (title.toVector ++ byline ++ abstracts ++ rule ++ body ++ floats ++ back)
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString("", "\n\n", "\n")

  /** The block-level Markdown of an element's children, sections as headings from `level`. */
  private def blocks(e: Elem, level: com.histmersion.upstreams.jats.Jats.BlocksLevel): Vector[com.histmersion.upstreams.jats.Jats.BlocksResult] =
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

  private def block(e: Elem, level: com.histmersion.upstreams.jats.Jats.BlockLevel): Vector[com.histmersion.upstreams.jats.Jats.BlockResult] = e.name match
    case "sec" | "app" | "boxed-text" | "glossary" | "notes" | "fn-group" | "app-group" | "sec-meta" =>
      val heading = (e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("label")).map(runText).toList ++ e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title")).map(runText).toList)
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
      val ordered = e.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrN("list-type")).exists(t => t == "order" || t.startsWith("alpha") || t.startsWith("roman"))
      Vector(
        e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("list-item"))
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
      val label = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("label")).map((runText(_)).value.stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("caption")).toVector.flatMap(captionText)
      if label.isEmpty && cap.isEmpty then e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("fig")).flatMap(block(_, level))
      else Vector(_root_.com.histmersion.upstreams.jats.Jats.BlockResult((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim))
    case "table-wrap" | "table-wrap-group" =>
      val label = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("label")).map((runText(_)).value.stripSuffix(".")).filter(_.nonEmpty)
      val cap = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("caption")).toVector.flatMap(captionText)
      val head = Option
        .when(label.nonEmpty || cap.nonEmpty)((label.map(l => s"**$l.** ").getOrElse("") + cap.mkString(" ")).trim)
        .toVector
      head ++ e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("table")).flatMap(table) ++ e.kids("table-wrap").flatMap(block(_, level)) ++
        e.kids("table-wrap-foot").flatMap(blocks(_, level))
    case "disp-formula" => formula(e).map(f => s"$$$$$f$$$$").toVector
    case "ref-list"     => refList(e, level)
    case "def-list"     =>
      Vector(
        e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("def-item"))
          .map(d =>
            "- " + (d.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("term")).map(runText).getOrElse("") + ": " + d.kid("def").map(runText).getOrElse("")).trim
          )
          .mkString("\n")
      )
    case "fn"                                 => Vector(_root_.com.histmersion.upstreams.jats.Jats.BlockResult((runText(e)).value))
    case n if Skip(n) || n.startsWith("mml:") => Vector.empty
    case _                                    => blocks(e, level)

  private val BlockInP =
    Set("list", "disp-quote", "fig", "table-wrap", "disp-formula", "boxed-text", "def-list", "supplementary-material")

  private def captionText(c: Elem): Vector[com.histmersion.upstreams.jats.Jats.CaptionTextResult] =
    (c.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title")).map(runText).toVector ++ c.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("p")).map(runText)).filter((_).value.nonEmpty)

  private def table(t: Elem): Vector[com.histmersion.upstreams.jats.Jats.TableResult] =
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
      Vector(_root_.com.histmersion.upstreams.jats.Jats.TableResult((line(pad.head) +: line(Vector.fill(width)("---")) +: pad.tail.map(line)).mkString("\n")))

  private def formula(e: Elem): Option[com.histmersion.upstreams.jats.Jats.FormulaResult] =
    def find(x: Elem): Option[Elem] =
      if x.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("tex-math") then Some(x) else x.elems.iterator.flatMap(find).nextOption()
    find(e).map(t => (collapse(_root_.com.histmersion.upstreams.jats.Jats.CollapseS((text(t)).value))).value.stripPrefix("$").stripSuffix("$").trim).filter(_.nonEmpty)

  private def refList(e: Elem, level: com.histmersion.upstreams.jats.Jats.RefListLevel, parent: Option[com.histmersion.upstreams.jats.Jats.RefListParent] = None): Vector[com.histmersion.upstreams.jats.Jats.RefListResult] =
    val heading = e.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("title")).map(runText).filter((_).value.nonEmpty).getOrElse("References")
    val shown = Option.when(!parent.contains(heading))("#" * math.min(6, (level).value) + " " + heading)
    val refs = e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("ref")).map { r =>
      val label = r.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("label")).map(runText).filter((_).value.nonEmpty).map((_).value.stripSuffix(".") + ". ").getOrElse("")
      val cites = r.elems.filter(c => c.name.endsWith("citation")).map(citation).filter((_).value.nonEmpty)
      "- " + label + cites.mkString(" ")
    }
    shown.toVector ++
      Option.when(refs.nonEmpty)(refs.mkString("\n")).toVector ++
      e.kids(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidsN("ref-list")).flatMap(refList(_, _root_.com.histmersion.upstreams.jats.Jats.RefListLevel((level).value + 1), Some(heading)))

  /** A `mixed-citation` carries its own punctuation; an `element-citation` is bare fields, joined — a volume with its
    * issue and a page range read as one field (`522(7555):167–172`).
    */
  private def citation(c: Elem): com.histmersion.upstreams.jats.Jats.CitationResult =
    if c.name != _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("element-citation") then _root_.com.histmersion.upstreams.jats.Jats.CitationResult((runText(c)).value)
    else
      val fields: Vector[(com.histmersion.upstreams.jats.Jats.CitationFieldsPart1, com.histmersion.upstreams.jats.Jats.CitationFieldsPart2)] = c.elems.flatMap {
        case g if g.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("person-group") =>
          val names = g.elems
            .flatMap {
              case n if n.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("name") => Some(personName(n))
              case n if n.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("etal") => Some("et al.")
              case n                     => Some(runText(n))
            }
            .filter(_.nonEmpty)
          Option.when(names.nonEmpty)({ val refactorTuple = "person-group" -> names.mkString(", "); (_root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart1(refactorTuple._1), _root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart2(refactorTuple._2)) })
        case p if p.name == _root_.com.histmersion.upstreams.jats.Jats.Elem.Name("pub-id") =>
          Option.when(p.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrN("pub-id-type")).contains("doi"))({ val refactorTuple2 = "doi" -> ("doi:" + runText(p)); (_root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart1(refactorTuple2._1), _root_.com.histmersion.upstreams.jats.Jats.CitationFieldsPart2(refactorTuple2._2)) })
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
        case Vector() => _root_.com.histmersion.upstreams.jats.Jats.CitationResult("")
        case parts    => _root_.com.histmersion.upstreams.jats.Jats.CitationResult(parts.mkString("", ". ", "."))

  /** `Given Surname`, for the byline. */
  private def byName(n: Elem): com.histmersion.upstreams.jats.Jats.ByNameResult =
    _root_.com.histmersion.upstreams.jats.Jats.ByNameResult((n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("given-names")).map(runText).toList ++ n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("surname")).map(runText).toList ++
      n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("suffix")).map(runText).toList).filter((_).value.nonEmpty).mkString(" "))

  /** `Surname Given`, as references write it. */
  private def personName(n: Elem): com.histmersion.upstreams.jats.Jats.PersonNameResult =
    _root_.com.histmersion.upstreams.jats.Jats.PersonNameResult((n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("surname")).map(runText).toList ++ n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("given-names")).map(runText).toList ++
      n.kid(_root_.com.histmersion.upstreams.jats.Jats.Elem.KidN("suffix")).map(runText).toList).filter((_).value.nonEmpty).mkString(" "))

  /** An element's running text as one Markdown line. */
  def runText(e: Elem): com.histmersion.upstreams.jats.Jats.RunTextResult = _root_.com.histmersion.upstreams.jats.Jats.RunTextResult((collapse(_root_.com.histmersion.upstreams.jats.Jats.CollapseS((inlineRaw(e)).value))).value)

  private def inlineRaw(e: Elem): com.histmersion.upstreams.jats.Jats.InlineRawResult =
    e.children.map {
      case Text(s) => s
      case c: Elem =>
        c.name match
          case "italic" | "em"                               => wrap(_root_.com.histmersion.upstreams.jats.Jats.WrapMark("*"), _root_.com.histmersion.upstreams.jats.Jats.WrapS((inlineRaw(c)).value))
          case "bold" | "strong"                             => wrap(_root_.com.histmersion.upstreams.jats.Jats.WrapMark("**"), _root_.com.histmersion.upstreams.jats.Jats.WrapS((inlineRaw(c)).value))
          case "xref" if c.attr(_root_.com.histmersion.upstreams.jats.Jats.Elem.AttrN("ref-type")).contains("bibr") =>
            val t = collapse(_root_.com.histmersion.upstreams.jats.Jats.CollapseS((inlineRaw(c)).value))
            if (t).value.isEmpty then "" else s"[$t]"
          case "inline-formula"                                        => formula(c).map(f => s"$$$f$$").getOrElse("")
          case "break"                                                 => " "
          case "fn" | "table-wrap" | "fig" | "disp-formula" | "list"   => " " + collapse(_root_.com.histmersion.upstreams.jats.Jats.CollapseS((inlineRaw(c)).value)) + " "
          case n if Skip(n) || n.startsWith("mml:") || n == "tex-math" => ""
          case _                                                       => inlineRaw(c)
    }.mkString

  private def wrap(mark: com.histmersion.upstreams.jats.Jats.WrapMark, s: com.histmersion.upstreams.jats.Jats.WrapS): com.histmersion.upstreams.jats.Jats.WrapResult =
    val t = (s).value.trim
    if t.isEmpty then _root_.com.histmersion.upstreams.jats.Jats.WrapResult((s).value)
    else
      _root_.com.histmersion.upstreams.jats.Jats.WrapResult((if (s).value.headOption.exists(_.isWhitespace) then " " else "") + mark + t + mark +
        (if (s).value.lastOption.exists(_.isWhitespace) then " " else ""))

  private def text(e: Elem): com.histmersion.upstreams.jats.Jats.TextResult = _root_.com.histmersion.upstreams.jats.Jats.TextResult(e.children.map {
    case Text(s) => s
    case c: Elem => text(c)
  }.mkString)

  private def collapse(s: com.histmersion.upstreams.jats.Jats.CollapseS): com.histmersion.upstreams.jats.Jats.CollapseResult = _root_.com.histmersion.upstreams.jats.Jats.CollapseResult((s).value.replaceAll("[\\s ]+", " ").trim)
