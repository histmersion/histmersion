package com.histmersion.domain.library.sources

import scala.annotation.tailrec
import com.histmersion.util.text.Fingerprint
import com.histmersion.api.given

/** The normalisers the source matcher compares with (docs/research-merge-import.md §5.2): folding, OpenRefine
  * fingerprints, title keys, personal names, Jaro-Winkler, token Jaccard and MinHash. All pure; JVM-only because of
  * `java.text.Normalizer` (in [[Fingerprint]]).
  */
object MergeText:

  // ── folding ──────────────────────────────────────────────────────────────────────────────────

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): [[Fingerprint.fold]]. */
  def fold(s: String): String = Fingerprint.fold(s)

  /** Folded alphanumeric tokens, in order. */
  def tokens(s: String): List[String] = Fingerprint.tokens(s)

  /** OpenRefine's key-collision fingerprint: folded tokens, de-duplicated, sorted, joined. */
  def fingerprint(s: String): String = Fingerprint.of(s).value

  private val stopwords: Set[String] = Set(
    "a",
    "an",
    "the",
    "of",
    "and",
    "or",
    "in",
    "on",
    "at",
    "to",
    "for",
    "from",
    "by",
    "with",
    "as",
    "into",
    "its",
    "their",
    "his",
    "her",
    "is",
    "are",
    "be",
    "was",
    "le",
    "la",
    "les",
    "de",
    "des",
    "du",
    "del",
    "della",
    "der",
    "die",
    "das",
    "den",
    "und",
    "von",
    "zu",
    "et",
    "el",
    "los",
    "las",
    "il",
    "un",
    "une",
    "ein",
    "eine",
    "y",
    "e"
  )

  def significant(ts: List[String]): List[String] = ts.filterNot(stopwords)

  // ── titles ───────────────────────────────────────────────────────────────────────────────────

  /** The main title: cut at the first `:`, ` - `, ` — `, ` – ` or `. ` (a subtitle follows). */
  def mainTitle(title: String): String =
    val cuts = List(":", " - ", " — ", " – ", ". ", "? ").flatMap(c => Option(title.indexOf(c)).filter(_ > 0))
    cuts.minOption.fold(title)(i => title.take(i)).trim

  /** The blocking key of a title: the first three significant tokens of the main title, sorted. */
  def titleKey(title: String): String = significant(tokens(mainTitle(title))).take(3).sorted.mkString(" ")

  // ── personal names ───────────────────────────────────────────────────────────────────────────

  /** A name as compared: folded surname tokens, folded forename tokens (an initial is one letter), and whether the
    * surname is known (`Surname, Given`) or guessed (`Given Surname`, where a multi-word surname cannot be told from a
    * middle name).
    */
  final case class Person(surname: List[String], forenames: List[String], surnameKnown: Boolean):
    /** The surname as one key (`lopezruiz`). */
    def key: String = surname.mkString
    def all: List[String] = forenames ++ surname

  /** Words that belong to a surname when they come before it (`van`, `de la`). */
  private val particles: Set[String] =
    Set(
      "van",
      "von",
      "der",
      "den",
      "de",
      "del",
      "della",
      "di",
      "da",
      "du",
      "la",
      "le",
      "ter",
      "ten",
      "bin",
      "ibn",
      "al",
      "el",
      "dos",
      "das",
      "zu",
      "vom",
      "st"
    )

  private val suffixes: Set[String] = Set("jr", "sr", "ii", "iii", "iv")

  private def nameTokens(s: String): List[String] =
    tokens(s).filterNot(t => t.forall(_.isDigit)).filterNot(suffixes)

  /** Parse `Surname, Given, 1850-1928`, `Given Surname`, `H. G. Guterbock`, `Lopez-Ruiz, Carolina`. Life dates and
    * Jr./Sr. are dropped; hyphens split a double surname into its words, so `López-Ruiz` and `Lopez Ruiz` compare
    * equal. `None` for an empty name.
    */
  def person(raw: String): Option[Person] =
    val parts = raw.split(",").toList.map(_.trim).filter(_.nonEmpty)
    parts match
      case Nil                                                       => None
      case first :: rest if rest.exists(p => nameTokens(p).nonEmpty) =>
        val surname = nameTokens(first)
        val forenames = rest.flatMap(nameTokens)
        Option.when(surname.nonEmpty)(Person(surname, forenames, surnameKnown = true))
      case _ =>
        val ts = nameTokens(raw)
        ts match
          case Nil       => None
          case List(one) => Some(Person(List(one), Nil, surnameKnown = true))
          case _         =>
            // the best guess: the last word, with any particles right before it — a guess still
            // (`Carolina Lopez Ruiz`), so `samePerson` tries every trailing run
            val (front, last) = (ts.init, ts.last)
            val parts = front.reverse.takeWhile(particles).reverse
            val forenames = front.dropRight(parts.length)
            Some(Person(parts :+ last, forenames, surnameKnown = false))

  /** Given names compatible: pairwise, the same initial, and where both are written out, the same name (or one a prefix
    * of the other). Missing forenames are compatible with anything.
    */
  def givenCompatible(a: List[String], b: List[String]): Boolean =
    a.zip(b).forall { (x, y) =>
      x.head == y.head && (x.length == 1 || y.length == 1 || x.startsWith(y) || y.startsWith(x))
    }

  /** Two names the same person: the surnames agree (for a guessed surname, any trailing run of its words may be the
    * surname — `Carolina Lopez Ruiz` against `López-Ruiz, Carolina`), and the forenames names that are left are
    * compatible. Pure.
    */
  def samePerson(a: Person, b: Person): Boolean =
    def splits(p: Person): List[(List[String], List[String])] =
      if p.surnameKnown then List(p.surname -> p.forenames)
      else
        val all = p.all
        (1 to all.length).toList.map(n => all.takeRight(n) -> all.dropRight(n))
    splits(a).exists { (sa, ga) =>
      splits(b).exists((sb, gb) => sa.mkString == sb.mkString && givenCompatible(ga, gb))
    }

  /** The blocking keys of a first author: the surname as one word and its last word. */
  def surnameKeys(raw: String): Set[String] =
    person(raw).toSet.flatMap(p => Set(p.key, p.surname.last))

  /** How two author lists agree, as the score of §5.2 reads it. */
  enum AuthorAgreement:
    case Same, FirstAuthor, Overlap, Disjoint, Unknown

  def authors(a: List[String], b: List[String]): AuthorAgreement =
    val pa = a.flatMap(person)
    val pb = b.flatMap(person)
    if pa.isEmpty || pb.isEmpty then AuthorAgreement.Unknown
    else
      // a greedy one-to-one pairing; author lists are short
      @tailrec def pair(rest: List[Person], n: Int, left: List[Person]): Int =
        rest match
          case Nil       => n
          case p :: tail =>
            left.indexWhere(samePerson(p, _)) match
              case -1 => pair(tail, n, left)
              case i  => pair(tail, n + 1, left.patch(i, Nil, 1))
      val matched = pair(pa, 0, pb)
      if matched == pa.length && matched == pb.length then AuthorAgreement.Same
      else if samePerson(pa.head, pb.head) then AuthorAgreement.FirstAuthor
      else if matched * 2 >= math.max(pa.length, pb.length) then AuthorAgreement.Overlap
      else if matched == 0 then AuthorAgreement.Disjoint
      else AuthorAgreement.Overlap

  // ── string similarity ────────────────────────────────────────────────────────────────────────

  /** Jaro-Winkler similarity in [0, 1] (Winkler 1990: prefix scale 0.1, up to 4 characters). */
  def jaroWinkler(s1: String, s2: String): Double =
    if s1 == s2 then 1.0
    else if s1.isEmpty || s2.isEmpty then 0.0
    else
      val range = math.max(0, math.max(s1.length, s2.length) / 2 - 1)
      val m1 = Array.fill(s1.length)(false)
      val m2 = Array.fill(s2.length)(false)
      val matches = s1.indices.count { i =>
        val lo = math.max(0, i - range)
        val hi = math.min(s2.length - 1, i + range)
        (lo to hi).find(j => !m2(j) && s1(i) == s2(j)) match
          case Some(j) => m1(i) = true; m2(j) = true; true
          case None    => false
      }
      if matches == 0 then 0.0
      else
        val a = s1.indices.filter(m1(_)).map(s1(_))
        val b = s2.indices.filter(m2(_)).map(s2(_))
        val t = a.zip(b).count((x, y) => x != y) / 2.0
        val m = matches.toDouble
        val jaro = (m / s1.length + m / s2.length + (m - t) / m) / 3.0
        val prefix = s1.zip(s2).take(4).takeWhile((x, y) => x == y).length
        jaro + prefix * 0.1 * (1 - jaro)

  def jaccard[A](a: Set[A], b: Set[A]): Double =
    if a.isEmpty && b.isEmpty then 1.0 else (a intersect b).size.toDouble / (a union b).size

  // ── identifiers ──────────────────────────────────────────────────────────────────────────────

  /** A DOI as a key — as V032's `histmersion_doi_key`: no resolver prefix, no `doi:`, lower case. */
  def doiKey(doi: String): Option[String] =
    Option(
      doi.trim
        .toLowerCase(java.util.Locale.ROOT)
        .replaceFirst("^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "")
    ).filter(_.nonEmpty)

  /** An ISBN as ISBN-13 digits; an ISBN-10 is converted. `None` when it is not a valid ISBN. */
  def isbn13(raw: String): Option[String] =
    val s = raw.filter(c => c.isDigit || c == 'X' || c == 'x').toUpperCase(java.util.Locale.ROOT)
    def check13(d: String): Boolean =
      d.length == 13 && d.forall(_.isDigit) &&
        d.zipWithIndex.map((c, i) => (c - '0') * (if i % 2 == 0 then 1 else 3)).sum % 10 == 0
    if s.length == 13 then Option.when(check13(s))(s)
    else if s.length == 10 && s.init.forall(_.isDigit) then
      val ok = s.zipWithIndex.map((c, i) => (if c == 'X' then 10 else c - '0') * (10 - i)).sum % 11 == 0
      Option.when(ok) {
        val core = "978" + s.init
        val sum = core.zipWithIndex.map((c, i) => (c - '0') * (if i % 2 == 0 then 1 else 3)).sum
        core + ((10 - sum % 10) % 10).toString
      }
    else None
