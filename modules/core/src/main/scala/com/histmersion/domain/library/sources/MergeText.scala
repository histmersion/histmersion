package com.histmersion
package domain.library.sources

import scala.annotation.tailrec
import com.histmersion.util.text.Fingerprint
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.Title
import com.histmersion.domain.AuthorName
import com.histmersion.domain.library.Isbn
import com.histmersion.lint.{tolerated, LintRule}

/** The normalisers the source matcher compares with (docs/research-merge-import.md §5.2): folding, OpenRefine
  * fingerprints, title keys, personal names, Jaro-Winkler, token Jaccard and MinHash. All pure; JVM-only because of
  * `java.text.Normalizer` (in [[Fingerprint]]).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object MergeText:

  /** A name, title or venue as written, before it is folded for comparison. */
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  /** A text in lower case with its diacritics removed (`Güterbock` → `guterbock`). */
  type Folded = Folded.T
  object Folded extends Newtype[String, Pure]

  /** One folded alphanumeric word. */
  type Token = Token.T
  object Token extends Newtype[String, Pure]

  /** A text as its folded words joined by single spaces. */
  type Words = Words.T
  object Words extends Newtype[String, Pure]

  /** The blocking key of a title: its first three significant words, sorted. */
  type TitleKey = TitleKey.T
  object TitleKey extends Newtype[String, Pure]

  /** A surname as one word (`lopezruiz`), or its last word. */
  type SurnameKey = SurnameKey.T
  object SurnameKey extends Newtype[String, Pure]

  /** How alike two texts are, in [0, 1]. */
  type Similarity = Similarity.T
  object Similarity extends Newtype[Double, Pure]

  /** A DOI as a source writes it: bare, `doi:`-prefixed or as a resolver URL. */
  type WrittenDoi = WrittenDoi.T
  object WrittenDoi extends Newtype[String, Pure]

  /** A DOI as a key: no resolver prefix, no `doi:`, lower case. */
  type DoiKey = DoiKey.T
  object DoiKey extends Newtype[String, Pure]

  // ── folding ──────────────────────────────────────────────────────────────────────────────────

  /** Lower case, diacritics removed (`Güterbock` → `guterbock`, `López` → `lopez`): [[Fingerprint.fold]]. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fold(s: Text): Folded = Folded(Fingerprint.fold(Fingerprint.FoldS(s.value)).value)

  /** Folded alphanumeric tokens, in order. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tokens(s: Text): List[Token] = Fingerprint.tokens(Fingerprint.TokensS(s.value)).map(t => Token(t.value))

  /** The folded words of a text, joined by single spaces. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def words(s: Text): Words = Words(tokens(s).map(_.value).mkString(" "))

  /** OpenRefine's key-collision fingerprint: folded tokens, de-duplicated, sorted, joined. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fingerprint(s: Text): Fingerprint = Fingerprint.of(Fingerprint.OfS(s.value))

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def significant(ts: List[Token]): List[Token] = ts.filterNot(t => stopwords(t.value))

  // ── titles ───────────────────────────────────────────────────────────────────────────────────

  /** The main title: cut at the first `:`, ` - `, ` — `, ` – ` or `. ` (a subtitle follows). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mainTitle(title: Title): Title =
    val text = title.value
    val cuts = List(":", " - ", " — ", " – ", ". ", "? ").flatMap(c => Option(text.indexOf(c)).filter(_ > 0))
    Title(cuts.minOption.fold(text)(i => text.take(i)).trim)

  /** The blocking key of a title: the first three significant tokens of the main title, sorted. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def titleKey(title: Title): TitleKey =
    TitleKey(significant(tokens(Text(mainTitle(title).value))).take(3).map(_.value).sorted.mkString(" "))

  // ── personal names ───────────────────────────────────────────────────────────────────────────

  /** A name as compared: folded surname tokens, folded forename tokens (an initial is one letter), and whether the
    * surname is known (`Surname, Given`) or guessed (`Given Surname`, where a multi-word surname cannot be told from a
    * middle name).
    */
  final case class Person(surname: List[Token], forenames: List[Token], surnameKnown: Person.SurnameKnown):
    /** The surname as one key (`lopezruiz`). */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def key: SurnameKey = SurnameKey(surname.map(_.value).mkString)
    def all: List[Token] = forenames ++ surname

  object Person:
    type SurnameKnown = SurnameKnown.T
    object SurnameKnown extends Newtype[Boolean, Pure]

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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def nameTokens(s: String): List[Token] =
    tokens(Text(s)).filterNot(t => t.value.forall(_.isDigit)).filterNot(t => suffixes(t.value))

  /** Parse `Surname, Given, 1850-1928`, `Given Surname`, `H. G. Guterbock`, `Lopez-Ruiz, Carolina`. Life dates and
    * Jr./Sr. are dropped; hyphens split a double surname into its words, so `López-Ruiz` and `Lopez Ruiz` compare
    * equal. `None` for an empty name.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def person(name: AuthorName): Option[Person] =
    val raw = name.value
    val parts = raw.split(",").toList.map(_.trim).filter(_.nonEmpty)
    parts match
      case Nil                                                       => None
      case first :: rest if rest.exists(p => nameTokens(p).nonEmpty) =>
        val surname = nameTokens(first)
        val forenames = rest.flatMap(nameTokens)
        Option.when(surname.nonEmpty)(Person(surname, forenames, surnameKnown = Person.SurnameKnown(true)))
      case _ =>
        val ts = nameTokens(raw)
        ts match
          case Nil       => None
          case List(one) => Some(Person(List(one), Nil, surnameKnown = Person.SurnameKnown(true)))
          case _         =>
            // the best guess: the last word, with any particles right before it — a guess still
            // (`Carolina Lopez Ruiz`), so `samePerson` tries every trailing run
            val (front, last) = (ts.init, ts.last)
            val parts = front.reverse.takeWhile(t => particles(t.value)).reverse
            val forenames = front.dropRight(parts.length)
            Some(Person(parts :+ last, forenames, surnameKnown = Person.SurnameKnown(false)))

  /** Given names compatible: pairwise, the same initial, and where both are written out, the same name (or one a prefix
    * of the other). Missing forenames are compatible with anything.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def givenCompatible(a: List[Token], b: List[Token]): Boolean =
    a.zip(b).forall { (x, y) =>
      x.value.head == y.value.head && (x.value.length == 1 || y.value.length == 1 || x.value.startsWith(
        y.value
      ) || y.value.startsWith(x.value))
    }

  /** Two names the same person: the surnames agree (for a guessed surname, any trailing run of its words may be the
    * surname — `Carolina Lopez Ruiz` against `López-Ruiz, Carolina`), and the forenames names that are left are
    * compatible. Pure.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def samePerson(a: Person, b: Person): Boolean =
    def splits(p: Person): List[(List[Token], List[Token])] =
      if p.surnameKnown.value then List(p.surname -> p.forenames)
      else
        val all = p.all
        (1 to all.length).toList.map(n => all.takeRight(n) -> all.dropRight(n))
    splits(a).exists { (sa, ga) =>
      splits(b).exists((sb, gb) => sa.map(_.value).mkString == sb.map(_.value).mkString && givenCompatible(ga, gb))
    }

  /** The blocking keys of a first author: the surname as one word and its last word. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def surnameKeys(name: AuthorName): Set[SurnameKey] =
    person(name).toSet.flatMap(p => Set(p.key, SurnameKey(p.surname.last.value)))

  /** How two author lists agree, as the score of §5.2 reads it. */
  enum AuthorAgreement derives CanEqual:
    case Same, FirstAuthor, Overlap, Disjoint, Unknown

  def authors(a: List[AuthorName], b: List[AuthorName]): AuthorAgreement =
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jaroWinkler(first: Words, second: Words): Similarity = Similarity(jaroWinklerOf(first.value, second.value))

  private def jaroWinklerOf(s1: String, s2: String): Double =
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def jaccard[A](a: Set[A], b: Set[A]): Similarity =
    Similarity(if a.isEmpty && b.isEmpty then 1.0 else (a intersect b).size.toDouble / (a union b).size)

  // ── identifiers ──────────────────────────────────────────────────────────────────────────────

  /** A DOI as a key — as V032's `histmersion_doi_key`: no resolver prefix, no `doi:`, lower case. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def doiKey(doi: WrittenDoi): Option[DoiKey] =
    Option(
      doi.value.trim
        .toLowerCase(java.util.Locale.ROOT)
        .replaceFirst("^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "")
    ).filter(_.nonEmpty).map(k => DoiKey(k))

  /** An ISBN as ISBN-13 digits; an ISBN-10 is converted. `None` when it is not a valid ISBN. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isbn13(raw: Isbn): Option[Isbn] =
    val s = raw.value.filter(c => c.isDigit || c == 'X' || c == 'x').toUpperCase(java.util.Locale.ROOT)
    def check13(d: String): Boolean =
      d.length == 13 && d.forall(_.isDigit) &&
        d.zipWithIndex.map((c, i) => (c - '0') * (if i % 2 == 0 then 1 else 3)).sum % 10 == 0
    if s.length == 13 then Option.when(check13(s))(Isbn(s))
    else if s.length == 10 && s.init.forall(_.isDigit) then
      val ok = s.zipWithIndex.map((c, i) => (if c == 'X' then 10 else c - '0') * (10 - i)).sum % 11 == 0
      Option.when(ok) {
        val core = "978" + s.init
        val sum = core.zipWithIndex.map((c, i) => (c - '0') * (if i % 2 == 0 then 1 else 3)).sum
        Isbn(core + ((10 - sum % 10) % 10).toString)
      }
    else None
