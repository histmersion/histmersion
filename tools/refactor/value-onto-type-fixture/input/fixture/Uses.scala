package fixture

import io.github.iltotore.iron.*

object Uses:
  def blank(title: Title): Boolean = title.value.isBlank
  def firstBlank(titles: List[Title]): Option[Title] = titles.find(_.value.isBlank)
  def anyBlank(titles: List[Title]): Boolean = titles.exists(t => t.value.isBlank)
  def prefixed(title: Title): Boolean = title.value.startsWith("A")
  def length(title: Title): Int = title.value.length
  def same(a: Title, b: Title): Boolean = a.value == b.value
  def differs(a: Title, b: Title): Boolean = a.value != b.value
  def mixed(a: Title, b: String): Boolean = a.value == b
  def across(a: Title, b: Name): Boolean = a.value == b.value
  def again(title: Title): Title = Title(title.value)
  def bridged(title: Title): Name = Name(title.value)
  def shown(title: Title): String = Dom.span(title.value)
  def plain(text: String): String = Dom.span(text)
  def undisplayed(name: Name): String = Dom.span(name.value)
  def tip(title: Title): String = Dom.title := title.value
  def tagged(title: Title): String = Dom.data := title.value
  def earlier(a: Year, b: Year): Boolean = a.value < b.value
  def bracketed(a: Year, b: Year): Boolean = (a).value < (b).value
