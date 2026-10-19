package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** THE PAIRING RULE (lint `article-critique-pairing`, docs/research-review-convergence.md §6.9): a
  * paragraph that cites one side of a disagreement edge (challenges, refutes, updates, superseded_by)
  * and names the other side's author in its prose cites both. The review's ledgers showed the failure
  * it catches: "The paragraph reports Gilan's criticism of Bachvarova but cites only [[kelly2021_gods]]"
  * — the critique is written, the work it answers is named, and the pair stays open because one
  * `[[citekey]]` is missing. Raised, "not fixed" and raised again across runs, it is mechanical.
  *
  * A hit is MECHANICAL when the name points at exactly one work the article already cites elsewhere:
  * the work's year stands beside a mention (or no other year does), no other candidate of the paragraph
  * shares the name, and no work the paragraph already cites does. Code may then append `[[missing]]`
  * to the paragraph's citation ([[repair]]) — it never brings a new source into the article (a new
  * source brings its own critiques, and nobody read its record). Any other hit, and an open pair whose other side the paragraph does not name, is
  * work for a writer: [[instruction]] says exactly where and what.
  *
  * Everything here is pure: the caller resolves links, reads the edges and the works' names. */
object CritiquePairing:

  /** A source as the rule reads it: the surnames its prose may name it by, and its year. */
  final case class Work(citekey: String, names: List[String], year: Option[Int])

  /** One paragraph (unit `unit` of [[Critiques.units]], body lines `from`…`to`) that cites `cited` and
    * names `named` — an author of `missing`, joined to `cited` by a disagreement edge (`edges`, as
    * `[[a]] kind [[b]]`) — without citing it. */
  final case class Hit(unit: Int, from: Int, to: Int, where: String, cited: String, missing: String,
      edges: List[String], named: String, mechanical: Boolean) derives KindlingsCodecAsObject:
    def ref: CritiqueRef = CritiqueRef(cited, missing)
    /** One line, as the lint and a run's warnings show it. */
    def line: String =
      s"paragraph \"$where\" names $named and cites [[$cited]] (${edges.mkString("; ")}) but not [[$missing]] — " +
        (if mechanical then s"cite [[$missing]] there (fixable)"
         else s"cite [[$missing]] there if the name means that work (code does not: the name is shared, or the article cites it nowhere else)")

  // ── names ──────────────────────────────────────────────────────────────────────────────────────

  private def letters(s: String): String = LatinFold.fold(s).toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z')
  private def ascii(s: String): String   = s.toLowerCase(java.util.Locale.ROOT).filter(c => c >= 'a' && c <= 'z')

  /** The surnames a source is named by in prose: the capitalised words of its authors that are the
    * citekey's leading name (`latacz2004_troy` → Latacz; `drews1993_endbronze` with the author stored
    * as "Drews Robert" → Drews; `bjrn2024_twenty` → Bjørn); when none is, the first author's last word
    * (or the part before a comma). Words under three letters are never names here. */
  def names(citekey: String, authors: List[String]): List[String] =
    val lead   = citekey.takeWhile(_.isLetter).toLowerCase(java.util.Locale.ROOT)
    val words  = authors.flatMap(_.split("[\\s,]+").toList).map(_.trim.stripSuffix(".")).filter(w => w.length >= 3 && w.head.isUpper)
    val byKey  = words.filter(w => lead.nonEmpty && (letters(w) == lead || ascii(w) == lead))
    val first  = authors.headOption.map(_.trim).filter(_.nonEmpty).map(a =>
      if a.contains(",") then a.takeWhile(_ != ',').trim else a.split("\\s+").last.trim)
    (if byKey.nonEmpty then byKey else first.toList).filter(n => n.length >= 3 && n.head.isUpper).distinct

  // ── finding the hits ───────────────────────────────────────────────────────────────────────────

  private val LinkRe = """!?\\?\[\\?\[[^\[\]\n]+?\\?\]\\?\]""".r
  private val YearRe = """(?<!\d)(1[0-9]\d\d|20\d\d)(?!\d)""".r

  /** The words of a text: whitespace-separated, punctuation-only tokens left out. */
  private def words(text: String): List[String] = text.split("\\s+").toList.filter(_.exists(_.isLetterOrDigit))

  /** A unit's prose: its links blanked out (a citekey is no mention of a name). */
  private def prose(text: String): String = LinkRe.replaceAllIn(text, " ")

  /** How a name is mentioned in `text`: None when it is not; else whether the work's year stands within
    * 25 characters after a mention (Some(true)), only other years do (conflict: the name means
    * another work of the author — never a hit), or no year does. */
  private enum Mention:
    case Strong, Weak, Conflict

  private def mention(text: String, name: String, year: Option[Int]): Option[Mention] =
    val re = ("(?<!\\p{L})" + java.util.regex.Pattern.quote(name) + "(?!\\p{L})").r
    val ms = re.findAllMatchIn(text).toList
    if ms.isEmpty then None
    else
      val near = ms.map(m => YearRe.findAllMatchIn(text.substring(m.end, math.min(text.length, m.end + 25))).map(_.group(1).toInt).toSet)
      if year.exists(y => near.exists(_(y))) then Some(Mention.Strong)
      else if near.forall(_.nonEmpty) then Some(Mention.Conflict)
      else Some(Mention.Weak)

  private def edgeLine(e: CoverageEdge): String = s"[[${e.from}]] ${e.kind} [[${e.to}]]"

  /** Every hit of `body`: per paragraph, each cited source's disagreement partners (`edges`, canonical
    * citekeys) that the paragraph names by surname ([[names]] via `work`) and does not cite.
    * `resolve` maps a `[[target]]` to its canonical citekey (None for an article or nothing). */
  def hits(body: String, resolve: String => Option[String], edges: List[CoverageEdge],
      work: String => Option[Work]): List[Hit] =
    val es = edges.filter(e => Critiques.Kinds.contains(e.kind) && e.from != e.to)
    val blocks = Critiques.units(body)
    val citedAnywhere = blocks.flatMap(u => ArticleLinks.targets(u.text).flatMap(resolve)).toSet
    blocks.zipWithIndex.flatMap { (u, i) =>
      val cited = ArticleLinks.targets(u.text).flatMap(resolve).distinct
      val text  = prose(u.text)
      val where = words(text).take(8).mkString(" ")
      // every partner of every cited source, with the edges between them
      val partners = cited.flatMap { x =>
        es.filter(e => e.from == x || e.to == x).map(e => (x, if e.from == x then e.to else e.from, e))
      }.filterNot((_, y, _) => cited.contains(y))
        .groupBy((x, y, _) => (x, y)).toList.sortBy(_._1).map { case ((x, y), xs) => (x, y, xs.map(_._3)) }
      val citedNames = cited.flatMap(work).flatMap(_.names).toSet
      val candidates = partners.flatMap { (x, y, xes) =>
        work(y).toList.flatMap { w =>
          w.names.flatMap(n => mention(text, n, w.year).filterNot(_ == Mention.Conflict).map(m => (x, y, xes, n, m))).headOption
        }
      }
      // a name two candidates share, or a cited work shares, needs the year beside it
      val byName = candidates.groupBy(_._4)
      candidates.groupBy(_._2).toList.sortBy(_._1).map { case (y, cs) =>
        val (x, _, xes, n, m) = cs.head
        val shared = byName(n).map(_._2).distinct.size > 1 || citedNames(n)
        Hit(i, u.from, u.to, where, x, y, cs.flatMap(_._3).distinct.map(edgeLine), n,
          mechanical = citedAnywhere(y) && (m == Mention.Strong || !shared))
      }
    }

  // ── the repair ─────────────────────────────────────────────────────────────────────────────────

  /** Apply the mechanical hits: each paragraph's missing citekeys appended after its last `[[link]]`
    * (`[[kelly2021_gods]]` → `[[kelly2021_gods]] [[bachvarova2016_hittite]]`). Returns the body and the
    * hits applied. Non-mechanical hits are left alone. */
  def repair(body: String, hs: List[Hit]): (String, List[Hit]) =
    val todo = hs.filter(_.mechanical).groupBy(h => (h.from, h.to))
    if todo.isEmpty then (body, Nil)
    else
      val crlf  = body.contains("\r\n")
      val lines = body.replace("\r\n", "\n").split("\n", -1).toVector
      val (out, done) = todo.toList.sortBy(_._1).foldLeft((lines, List.empty[Hit])) { case ((ls, acc), ((from, to), xs)) =>
        val add = xs.map(_.missing).distinct
        (to to from by -1).find(j => ls(j).contains("]]")) match
          case None => (ls, acc)
          case Some(j) =>
            val l   = ls(j)
            val at  = l.lastIndexOf("]]") + 2
            val ins = add.map(ck => s" [[$ck]]").mkString
            (ls.updated(j, l.substring(0, at) + ins + l.substring(at)), acc ++ xs.distinctBy(_.missing))
      }
      val text = out.mkString("\n")
      (if crlf then text.replace("\n", "\r\n") else text, done.sortBy(h => (h.unit, h.missing)))

  // ── what a writer is told ──────────────────────────────────────────────────────────────────────

  /** Where and how to answer an open pair a writer must take up: the paragraph that cites the
    * criticised work (the one at `near`, a unit index, when it cites it — the paragraph a finding
    * quotes — else the first that does), and what to write there. None when no paragraph cites it. */
  def instruction(body: String, p: CritiquePair, resolve: String => Option[String], near: Option[Int] = None): Option[String] =
    val crit   = p.criticised.citekey
    val critic = p.critic.citekey
    val us     = Critiques.units(body).zipWithIndex
    def cites(u: Critiques.Block) = ArticleLinks.targets(u.text).flatMap(resolve).contains(crit)
    near.flatMap(n => us.find((u, i) => i == n && cites(u))).orElse(us.find((u, _) => cites(u))).map { (u, _) =>
      val head = words(prose(u.text)).take(10).mkString(" ")
      s"${p.ref.wire}: in the paragraph beginning \"$head\" (it cites [[$crit]]), add one sentence stating the " +
        s"${p.kinds.mkString("/")} from [[$critic]]'s record and cite [[$critic]] in that paragraph — or, when it does " +
        "not touch what that paragraph says, set the pair aside with a reason"
    }

  /** The resolver a corpus-wide pass uses: a citekey itself, an old citekey renamed away (`aliases`),
    * else the unique `<target>_…` citekey — the page's own order (exact first, then the unique short
    * form). */
  def resolver(citekeys: Set[String], aliases: Map[String, String]): String => Option[String] =
    val sorted = citekeys.toVector.sorted
    t =>
      val k = t.trim.toLowerCase(java.util.Locale.ROOT)
      if citekeys(k) then Some(k)
      else aliases.get(k).filter(citekeys).orElse {
        val pre = sorted.filter(_.startsWith(k + "_"))
        Option.when(pre.size == 1)(pre.head)
      }
