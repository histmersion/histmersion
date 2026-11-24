package fixture

object Links:
  def heading(title: String): String = s"# $title"
  def blankOr(title: String, other: String): String = if title.isBlank then other else keep(title).toUpperCase
  def keep(title: String): String = title
  def checked(title: Title): Boolean = title.isBlank
  def first(titles: Option[String]): Option[String] = titles

object Callers:
  def a(t: Title): String = Links.heading(t.value)
  def b(raw: String): String = Links.heading(raw)
  def c(t: Title): String = Links.blankOr(other = "x", title = t.value)
  def d(t: Title): Boolean = Links.checked(t)
