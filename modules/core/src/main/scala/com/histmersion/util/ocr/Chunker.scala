package com.histmersion.util.ocr

/** Splits a Markdown body into FTS chunks — pure, no I/O, no config lookups.
  *
  * Why: Postgres caps a single `tsvector` at ~1 MB, and `ocr_markdown` routinely exceeds that, so the body cannot be
  * indexed as one row. Chunks are cut on Markdown headings first (each chunk then has a meaningful title for snippets)
  * and, inside a section, on paragraph boundaries, so a chunk never exceeds `maxChars` (config
  * `histmersion.limits.chunk-chars`).
  *
  * Guarantees (all unit-tested):
  *   - NUL bytes are stripped (Postgres `text` rejects them).
  *   - `ord` is 0-based and strictly increasing in document order.
  *   - a chunk never ends in the middle of a word, unless a single token is itself longer than `maxChars` (not a word —
  *     an OCR artefact; it is hard-split so chunks stay bounded).
  *   - no chunk is empty or blank; a heading with no text of its own produces no chunk (its sub-sections carry it in
  *     their breadcrumb).
  */
object Chunker:

  private val Heading = """^(#{1,6})\s+(.*?)\s*#*\s*$""".r

  def chunk(markdown: String, maxChars: Int): List[Chunk] =
    val max = math.max(1, maxChars)
    val text = Option(markdown).getOrElse("").replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n')
    if text.isBlank then Nil
    else
      val out = List.newBuilder[Chunk]
      var ord = 0
      // stack of (level, title) — the enclosing headings, so an h3 inherits its h1/h2
      var stack = List.empty[(Int, String)]
      val buf = new StringBuilder

      def breadcrumb: Option[String] =
        val path = stack.reverse.map(_._2).filter(_.nonEmpty)
        if path.isEmpty then None else Some(path.mkString(" > "))

      def emit(heading: Option[String]): Unit =
        val section = buf.toString
        buf.clear()
        split(section, max).foreach { body =>
          out += Chunk(ord, heading, body)
          ord += 1
        }

      text.linesIterator.foreach {
        case Heading(hashes, title) =>
          emit(breadcrumb) // close the section that ends at this heading, under the OLD breadcrumb
          val level = hashes.length
          stack = (level, title) :: stack.dropWhile(_._1 >= level)
        case line =>
          buf.append(line).append('\n')
      }
      emit(breadcrumb)
      out.result()

  /** Section text → bodies of at most `max` chars, cut on blank lines, then on whitespace. */
  private def split(section: String, max: Int): List[String] =
    val paragraphs = section.split("\n[ \t]*\n").toList.map(_.trim).filter(_.nonEmpty)
    val out = List.newBuilder[String]
    val cur = new StringBuilder

    def flush(): Unit =
      val s = cur.toString.trim
      cur.clear()
      if s.nonEmpty then out += s

    paragraphs.foreach { p =>
      if p.length > max then
        flush()
        hardSplit(p, max).foreach(out += _)
      else
        if cur.nonEmpty && cur.length + 2 + p.length > max then flush()
        if cur.nonEmpty then cur.append("\n\n")
        cur.append(p)
    }
    flush()
    out.result()

  /** One over-long paragraph → pieces of at most `max` chars, cut at the last whitespace that fits. Index-based (no
    * repeated `substring` of the tail): a 5 MB body must not be quadratic.
    */
  private def hardSplit(p: String, max: Int): List[String] =
    val out = List.newBuilder[String]
    var start = 0
    while start < p.length do
      while start < p.length && p.charAt(start).isWhitespace do start += 1
      if start < p.length then
        var end = math.min(p.length, start + max)
        if end < p.length then
          var back = end
          while back > start && !p.charAt(back).isWhitespace do back -= 1
          if back > start then end = back // else: a single token longer than max → hard cut
        val piece = p.substring(start, end).trim
        if piece.nonEmpty then out += piece
        start = end
    out.result()
