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
  type SamePersonSplitsResultPart1 = SamePersonSplitsResultPart1.T
  object SamePersonSplitsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SamePersonSplitsResultPart2 = SamePersonSplitsResultPart2.T
  object SamePersonSplitsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsPairResult = AuthorsPairResult.T
  object AuthorsPairResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsPairN = AuthorsPairN.T
  object AuthorsPairN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn13Check13Result = Isbn13Check13Result.T
  object Isbn13Check13Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn13Check13D = Isbn13Check13D.T
  object Isbn13Check13D extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FoldResult = FoldResult.T
  object FoldResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldS = FoldS.T
  object FoldS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensResult = TokensResult.T
  object TokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TokensS = TokensS.T
  object TokensS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FingerprintResult = FingerprintResult.T
  object FingerprintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FingerprintS = FingerprintS.T
  object FingerprintS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Stopwords = Stopwords.T
  object Stopwords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignificantResult = SignificantResult.T
  object SignificantResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SignificantTs = SignificantTs.T
  object SignificantTs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MainTitleResult = MainTitleResult.T
  object MainTitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MainTitleTitle = MainTitleTitle.T
  object MainTitleTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleKeyResult = TitleKeyResult.T
  object TitleKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleKeyTitle = TitleKeyTitle.T
  object TitleKeyTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Particles = Particles.T
  object Particles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Suffixes = Suffixes.T
  object Suffixes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameTokensResult = NameTokensResult.T
  object NameTokensResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameTokensS = NameTokensS.T
  object NameTokensS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PersonRaw = PersonRaw.T
  object PersonRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GivenCompatibleResult = GivenCompatibleResult.T
  object GivenCompatibleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GivenCompatibleA = GivenCompatibleA.T
  object GivenCompatibleA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GivenCompatibleB = GivenCompatibleB.T
  object GivenCompatibleB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SamePersonResult = SamePersonResult.T
  object SamePersonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SurnameKeysResult = SurnameKeysResult.T
  object SurnameKeysResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SurnameKeysRaw = SurnameKeysRaw.T
  object SurnameKeysRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsA = AuthorsA.T
  object AuthorsA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsB = AuthorsB.T
  object AuthorsB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JaroWinklerResult = JaroWinklerResult.T
  object JaroWinklerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JaroWinklerS1 = JaroWinklerS1.T
  object JaroWinklerS1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JaroWinklerS2 = JaroWinklerS2.T
  object JaroWinklerS2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JaccardResult = JaccardResult.T
  object JaccardResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoiKeyResult = DoiKeyResult.T
  object DoiKeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn13Result = Isbn13Result.T
  object Isbn13Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn13Raw = Isbn13Raw.T
  object Isbn13Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fold(s: com.histmersion.domain.library.sources.MergeText.FoldS): com.histmersion.domain.library.sources.MergeText.FoldResult = Fingerprint.fold(s)

  /** Folded alphanumeric tokens, in order. */
  def tokens(s: com.histmersion.domain.library.sources.MergeText.TokensS): List[com.histmersion.domain.library.sources.MergeText.TokensResult] = Fingerprint.tokens(s)

  /** OpenRefine's key-collision fingerprint: folded tokens, de-duplicated, sorted, joined. */
  def fingerprint(s: com.histmersion.domain.library.sources.MergeText.FingerprintS): com.histmersion.domain.library.sources.MergeText.FingerprintResult = Fingerprint.of(s).value

  private val stopwords: Set[com.histmersion.domain.library.sources.MergeText.Stopwords] = Set(
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

  def significant(ts: List[com.histmersion.domain.library.sources.MergeText.SignificantTs]): List[com.histmersion.domain.library.sources.MergeText.SignificantResult] = ts.filterNot(stopwords)

  // ── titles ───────────────────────────────────────────────────────────────────────────────────

  /** The main title: cut at the first `:`, ` - `, ` — `, ` – ` or `. ` (a subtitle follows). */
  def mainTitle(title: com.histmersion.domain.library.sources.MergeText.MainTitleTitle): com.histmersion.domain.library.sources.MergeText.MainTitleResult =
    val cuts = List(":", " - ", " — ", " – ", ". ", "? ").flatMap(c => Option(title.indexOf(c)).filter(_ > 0))
    cuts.minOption.fold(title)(i => title.take(i)).trim

  /** The blocking key of a title: the first three significant tokens of the main title, sorted. */
  def titleKey(title: com.histmersion.domain.library.sources.MergeText.TitleKeyTitle): com.histmersion.domain.library.sources.MergeText.TitleKeyResult = significant(tokens(mainTitle(title))).take(3).sorted.mkString(" ")

  // ── personal names ───────────────────────────────────────────────────────────────────────────

  /** A name as compared: folded surname tokens, folded forename tokens (an initial is one letter), and whether the
    * surname is known (`Surname, Given`) or guessed (`Given Surname`, where a multi-word surname cannot be told from a
    * middle name).
    */
  final case class Person(surname: List[Person.Surname], forenames: List[Person.Forenames], surnameKnown: Person.SurnameKnown):
    /** The surname as one key (`lopezruiz`). */
    def key: com.histmersion.domain.library.sources.MergeText.Person.KeyResult = surname.mkString
    def all: List[com.histmersion.domain.library.sources.MergeText.Person.AllResult] = forenames ++ surname

  object Person:
    type KeyResult = KeyResult.T
    object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllResult = AllResult.T
    object AllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Surname = Surname.T
    object Surname extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Forenames = Forenames.T
    object Forenames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SurnameKnown = SurnameKnown.T
    object SurnameKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Words that belong to a surname when they come before it (`van`, `de la`). */
  private val particles: Set[com.histmersion.domain.library.sources.MergeText.Particles] =
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

  private val suffixes: Set[com.histmersion.domain.library.sources.MergeText.Suffixes] = Set("jr", "sr", "ii", "iii", "iv")

  private def nameTokens(s: com.histmersion.domain.library.sources.MergeText.NameTokensS): List[com.histmersion.domain.library.sources.MergeText.NameTokensResult] =
    tokens(s).filterNot(t => t.forall(_.isDigit)).filterNot(suffixes)

  /** Parse `Surname, Given, 1850-1928`, `Given Surname`, `H. G. Guterbock`, `Lopez-Ruiz, Carolina`. Life dates and
    * Jr./Sr. are dropped; hyphens split a double surname into its words, so `López-Ruiz` and `Lopez Ruiz` compare
    * equal. `None` for an empty name.
    */
  def person(raw: com.histmersion.domain.library.sources.MergeText.PersonRaw): Option[Person] =
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
  def givenCompatible(a: List[com.histmersion.domain.library.sources.MergeText.GivenCompatibleA], b: List[com.histmersion.domain.library.sources.MergeText.GivenCompatibleB]): com.histmersion.domain.library.sources.MergeText.GivenCompatibleResult =
    a.zip(b).forall { (x, y) =>
      x.head == y.head && (x.length == 1 || y.length == 1 || x.startsWith(y) || y.startsWith(x))
    }

  /** Two names the same person: the surnames agree (for a guessed surname, any trailing run of its words may be the
    * surname — `Carolina Lopez Ruiz` against `López-Ruiz, Carolina`), and the forenames names that are left are
    * compatible. Pure.
    */
  def samePerson(a: Person, b: Person): com.histmersion.domain.library.sources.MergeText.SamePersonResult =
    def splits(p: Person): List[(List[com.histmersion.domain.library.sources.MergeText.SamePersonSplitsResultPart1], List[com.histmersion.domain.library.sources.MergeText.SamePersonSplitsResultPart2])] =
      if p.surnameKnown then List(p.surname -> p.forenames)
      else
        val all = p.all
        (1 to all.length).toList.map(n => all.takeRight(n) -> all.dropRight(n))
    splits(a).exists { (sa, ga) =>
      splits(b).exists((sb, gb) => sa.mkString == sb.mkString && givenCompatible(ga, gb))
    }

  /** The blocking keys of a first author: the surname as one word and its last word. */
  def surnameKeys(raw: com.histmersion.domain.library.sources.MergeText.SurnameKeysRaw): Set[com.histmersion.domain.library.sources.MergeText.SurnameKeysResult] =
    person(raw).toSet.flatMap(p => Set(p.key, p.surname.last))

  /** How two author lists agree, as the score of §5.2 reads it. */
  enum AuthorAgreement:
    case Same, FirstAuthor, Overlap, Disjoint, Unknown

  def authors(a: List[com.histmersion.domain.library.sources.MergeText.AuthorsA], b: List[com.histmersion.domain.library.sources.MergeText.AuthorsB]): AuthorAgreement =
    val pa = a.flatMap(person)
    val pb = b.flatMap(person)
    if pa.isEmpty || pb.isEmpty then AuthorAgreement.Unknown
    else
      // a greedy one-to-one pairing; author lists are short
      @tailrec def pair(rest: List[Person], n: com.histmersion.domain.library.sources.MergeText.AuthorsPairN, left: List[Person]): com.histmersion.domain.library.sources.MergeText.AuthorsPairResult =
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
  def jaroWinkler(s1: com.histmersion.domain.library.sources.MergeText.JaroWinklerS1, s2: com.histmersion.domain.library.sources.MergeText.JaroWinklerS2): com.histmersion.domain.library.sources.MergeText.JaroWinklerResult =
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

  def jaccard[A](a: Set[A], b: Set[A]): com.histmersion.domain.library.sources.MergeText.JaccardResult =
    if a.isEmpty && b.isEmpty then 1.0 else (a intersect b).size.toDouble / (a union b).size

  // ── identifiers ──────────────────────────────────────────────────────────────────────────────

  /** A DOI as a key — as V032's `histmersion_doi_key`: no resolver prefix, no `doi:`, lower case. */
  def doiKey(doi: com.histmersion.domain.library.Doi): Option[com.histmersion.domain.library.sources.MergeText.DoiKeyResult] =
    Option(
      doi.trim
        .toLowerCase(java.util.Locale.ROOT)
        .replaceFirst("^(https?://(dx\\.)?doi\\.org/|doi:\\s*)", "")
    ).filter(_.nonEmpty)

  /** An ISBN as ISBN-13 digits; an ISBN-10 is converted. `None` when it is not a valid ISBN. */
  def isbn13(raw: com.histmersion.domain.library.sources.MergeText.Isbn13Raw): Option[com.histmersion.domain.library.sources.MergeText.Isbn13Result] =
    val s = raw.filter(c => c.isDigit || c == 'X' || c == 'x').toUpperCase(java.util.Locale.ROOT)
    def check13(d: com.histmersion.domain.library.sources.MergeText.Isbn13Check13D): com.histmersion.domain.library.sources.MergeText.Isbn13Check13Result =
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
