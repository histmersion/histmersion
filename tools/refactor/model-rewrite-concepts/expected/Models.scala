//> using scala 3.10.0
package conceptfixture

final case class Report(label: conceptfixture.Label = conceptfixture.Label(""), title: conceptfixture.Title = conceptfixture.Title(""), bytes: conceptfixture.ByteCount = conceptfixture.ByteCount(0L), lat: conceptfixture.Latitude = conceptfixture.Latitude(0.0), doi: Option[conceptfixture.Doi] = None, authors: List[conceptfixture.AuthorName] = (Nil).map(conceptfixture.AuthorName.apply), year: conceptfixture.Year = conceptfixture.Year(0))
final case class Unrelated(label: String, bytes: Long)

object Calls:
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
