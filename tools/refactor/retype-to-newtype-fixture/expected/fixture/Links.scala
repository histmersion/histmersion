package fixture

object Links:
  def heading(title: Title): String = s"# ${title.value}"
  def blankOr(title: Title, other: String): String = if title.isBlank then other else keep(title.value).toUpperCase
  def keep(title: String): String = title
  def checked(title: Title): Boolean = title.isBlank
  def first(titles: Option[String]): Option[String] = titles

object Callers:
  def a(t: Title): String = Links.heading(t)
  def b(raw: String): String = Links.heading(Title(raw))
  def c(t: Title): String = Links.blankOr(other = "x", title = t)
  def d(t: Title): Boolean = Links.checked(t)
