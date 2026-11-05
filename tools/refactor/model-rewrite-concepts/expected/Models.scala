//> using scala 3.10.0
package conceptfixture

final case class Report(label: conceptfixture.Label = conceptfixture.Label(""), title: conceptfixture.Title = conceptfixture.Title(""), bytes: conceptfixture.ByteCount = conceptfixture.ByteCount(0L), lat: conceptfixture.Latitude = conceptfixture.Latitude(0.0), doi: Option[conceptfixture.Doi] = None, authors: List[conceptfixture.AuthorName] = (Nil).map(conceptfixture.AuthorName.apply), year: conceptfixture.Year = conceptfixture.Year(0))
final case class Unrelated(label: String, bytes: Long)
final case class Quantities(samples: List[conceptfixture.ByteCount], maybe: Option[conceptfixture.ByteCount])
final case class Nested(authors: Option[List[conceptfixture.AuthorName]] = None)

object Calls:
  def fetchLabel(label: conceptfixture.Label): String = label.value
  def fetch(using prefix: String)(reader: conceptfixture.Label => String, label: conceptfixture.Label): String = prefix + reader(label)
  def fetchRaw(using prefix: String)(reader: conceptfixture.Label => String): String = reader(conceptfixture.Label("raw"))
  def fetched: String = fetch(using "!")(fetchLabel, conceptfixture.Label("fetched"))
  def fetchedForwarded(r: Report): String = fetch(using "!")(fetchLabel, r.label)
  def fetchedRaw: String = fetchRaw(using "!")(fetchLabel)
  def fetchedLambda: String = fetch(using "!")(_ => "constant", conceptfixture.Label("unused"))
  def leading(using prefix: String)(label: conceptfixture.Label): String = prefix + label.value
  def leadingImplicit(using prefix: String): String = leading(conceptfixture.Label("implicit"))
  def leadingExplicit: String = leading(using "!")(conceptfixture.Label("explicit"))
  def leadingNamed: String = leading(using "?")(label = conceptfixture.Label("named"))
  def leadingForwarded(r: Report): String = leading(using "!")(r.label)
  def contextual(label: conceptfixture.Label)(using suffix: String): String = label.value + suffix
  def contextImplicit(using suffix: String): String = contextual(conceptfixture.Label("implicit"))
  def contextExplicit: String = contextual(conceptfixture.Label("explicit"))(using "!")
  def contextNamed: String = contextual(label = conceptfixture.Label("named"))(using "?")
  def contextForwarded(r: Report): String = contextual(r.label)(using "!")
  def unrelatedCurried(label: String)(suffix: String): String = label + suffix
  def blankToNone(s: Option[String]): Option[String] = s.map(_.trim).filter(_.nonEmpty)
  def normalizedDoi(p: Report, existing: Report) =
    p.doi.fold(existing.doi)(v => blankToNone(Some(v.value)).map(conceptfixture.Doi.apply))
  def nested: Nested = Nested((Some(List("a", "b"))).map(_.map(conceptfixture.AuthorName.apply)))
  def forwardNested(n: Nested): Nested = Nested(n.authors)
  def emptyNested: Nested = Nested((Some(Nil)).map(_.map(conceptfixture.AuthorName.apply)))
  var evaluations = 0
  def rawAuthors(): List[String] =
    evaluations += 1
    List("a", "b")
  def defaults: Report = Report()
  def positional: Report = Report(conceptfixture.Label("k"), conceptfixture.Title("t"), conceptfixture.ByteCount(5L), conceptfixture.Latitude(1.5), (Some("d")).map(conceptfixture.Doi.apply), (List("a", "b")).map(conceptfixture.AuthorName.apply), conceptfixture.Year(1999))
  def named: Report = Report(authors = (rawAuthors()).map(conceptfixture.AuthorName.apply), title = conceptfixture.Title("named"), bytes = conceptfixture.ByteCount(7L))
  def copied(r: Report): Report = r.copy(title = conceptfixture.Title("changed"))
  def forwarded(r: Report): Report = Report(r.label, r.title, r.bytes, r.lat, r.doi, r.authors, r.year)
  def label(r: Report): conceptfixture.Label = r.label
  def authors(r: Report): List[conceptfixture.AuthorName] = r.authors
  def echo(label: conceptfixture.Label): conceptfixture.Label = label
  def echoed: conceptfixture.Label = echo(conceptfixture.Label("echo"))
  def unrelated: Unrelated = Unrelated("raw", 3L)
  def aliases(keys: Set[conceptfixture.Label] = (Set.empty).map(conceptfixture.Label.apply)): Set[conceptfixture.Label] = keys
  def forwardedAliases(keys: Set[conceptfixture.Label]): Set[conceptfixture.Label] = aliases(keys)
  def aliasContains(keys: Set[conceptfixture.Label], label: conceptfixture.Label): Boolean = keys.contains(label)
  def repeatedAliases: Set[conceptfixture.Label] = aliases((Set("a", "a", "b")).map(conceptfixture.Label.apply))

object Rendering:
  def typedTotal(rs: List[Report]): conceptfixture.ByteCount = conceptfixture.ByteCount(rs.map(_.bytes.value.toLong).sum)
  def digits(n: Long): String = n.toString
  def totalText(rs: List[Report]): String = digits(typedTotal(rs).value.toLong)
  def totalRecord(rs: List[Report]): Report = Report(bytes = typedTotal(rs))
  def titles(rs: List[Report]): List[Report] = rs.sortBy(_.title.value.toString)
  def quantityTotal(q: Quantities): Long = q.samples.map(_.value.toLong).sum + q.maybe.map(_.value.toLong).toList.sum
  def below(rs: List[Report], limit: Report): List[Report] = rs.filter(_.bytes.value.toLong < limit.bytes.value.toLong)
  def sorted(rs: List[Report]): List[Report] = rs.sortBy(_.bytes.value.toLong)
  def total(rs: List[Report]): Long = rs.map(_.bytes.value.toLong).sum
  var evaluations = 0
  def observed(r: Report): Report =
    evaluations += 1
    r
  def strings(xs: List[String]): String = xs.mkString("|")
  def optional(s: Option[String]): String = s.getOrElse("missing")
  def render(r: Report): String = strings(observed(r).authors.map(_.value)) + optional(r.doi.map(_.value))
  def normalized(r: Report): Option[String] = r.doi.map(_.value).map(_.trim).filter(_.nonEmpty)
  def forwarded(r: Report): Report = Report(r.label, r.title, r.bytes, r.lat, r.doi, r.authors, r.year)
