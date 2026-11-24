package fixture

import io.github.iltotore.iron.*

object Uses:
  def blank(title: Title): Boolean = title.isBlank
  def firstBlank(titles: List[Title]): Option[Title] = titles.find(_.isBlank)
  def anyBlank(titles: List[Title]): Boolean = titles.exists(t => t.isBlank)
  def prefixed(title: Title): Boolean = title.startsWith("A")
  def length(title: Title): Int = title.value.length
  def same(a: Title, b: Title): Boolean = a == b
  def differs(a: Title, b: Title): Boolean = a != b
  def mixed(a: Title, b: String): Boolean = a.value == b
  def across(a: Title, b: Name): Boolean = a.value == b.value
  def again(title: Title): Title = title
  def bridged(title: Title): Name = Name(title.value)
  def shown(title: Title): String = Dom.span(title)
  def plain(text: String): String = Dom.span(text)
  def undisplayed(name: Name): String = Dom.span(name.value)
  def earlier(a: Year, b: Year): Boolean = a < b
