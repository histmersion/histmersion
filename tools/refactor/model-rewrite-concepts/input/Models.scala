//> using scala 3.10.0
package conceptfixture

final case class Report(label: String = "", title: String = "", bytes: Long = 0L, lat: Double = 0.0, doi: Option[String] = None, authors: List[String] = Nil, year: Int = 0)
final case class Unrelated(label: String, bytes: Long)
final case class Quantities(samples: List[Long], maybe: Option[Long])

object Calls:
  var evaluations = 0
  def rawAuthors(): List[String] =
    evaluations += 1
    List("a", "b")
  def defaults: Report = Report()
  def positional: Report = Report("k", "t", 5L, 1.5, Some("d"), List("a", "b"), 1999)
  def named: Report = Report(authors = rawAuthors(), title = "named", bytes = 7L)
  def copied(r: Report): Report = r.copy(title = "changed")
  def forwarded(r: Report): Report = Report(r.label, r.title, r.bytes, r.lat, r.doi, r.authors, r.year)
  def label(r: Report): String = r.label
  def authors(r: Report): List[String] = r.authors
  def echo(label: String): String = label
  def echoed: String = echo("echo")
  def unrelated: Unrelated = Unrelated("raw", 3L)
  def aliases(keys: Set[String] = Set.empty): Set[String] = keys
  def forwardedAliases(keys: Set[String]): Set[String] = aliases(keys)
  def aliasContains(keys: Set[String], label: String): Boolean = keys.contains(label)
  def repeatedAliases: Set[String] = aliases(Set("a", "a", "b"))

object Rendering:
  def typedTotal(rs: List[Report]): Long = rs.map(_.bytes).sum
  def digits(n: Long): String = n.toString
  def totalText(rs: List[Report]): String = digits(typedTotal(rs))
  def totalRecord(rs: List[Report]): Report = Report(bytes = typedTotal(rs))
  def titles(rs: List[Report]): List[Report] = rs.sortBy(_.title)
  def quantityTotal(q: Quantities): Long = q.samples.sum + q.maybe.toList.sum
  def below(rs: List[Report], limit: Report): List[Report] = rs.filter(_.bytes < limit.bytes)
  def sorted(rs: List[Report]): List[Report] = rs.sortBy(_.bytes)
  def total(rs: List[Report]): Long = rs.map(_.bytes).sum
  var evaluations = 0
  def observed(r: Report): Report =
    evaluations += 1
    r
  def strings(xs: List[String]): String = xs.mkString("|")
  def optional(s: Option[String]): String = s.getOrElse("missing")
  def render(r: Report): String = strings(observed(r).authors) + optional(r.doi)
  def normalized(r: Report): Option[String] = r.doi.map(_.trim).filter(_.nonEmpty)
  def forwarded(r: Report): Report = Report(r.label, r.title, r.bytes, r.lat, r.doi, r.authors, r.year)
