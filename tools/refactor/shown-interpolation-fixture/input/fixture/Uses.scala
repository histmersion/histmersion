package com.histmersion
package fixture

import io.github.iltotore.iron.*

object Uses:
  def counted(title: Title, copies: Int): String = s"$copies copies of $title"
  def unwrapped(title: Title): String = s"title: ${title.value}"
  def labelled(wire: Wire): String = s"state-$wire"
  def undisplayed(title: Title, name: Name): String = s"$title by $name"
  def listed(title: Title, years: List[Int]): String = s"$title in $years"
  def plain(copies: Int): String = s"$copies copies"
  def counting(): Int = 3
  def called(title: Title): String = s"$title, ${counting()} times"
  def numbered(count: Count): String = s"entry ${count.value}"
  def joined(title: Title, names: List[String]): String = s"$title by ${names.mkString(", ")}, ${names.size}"
  def computed(title: Title, n: Int): String = s"$title ${n + 1} copie${if n > 1 then "s" else ""}"
  def generic(title: Title, name: Option[String]): String = s"$title by ${name.getOrElse("")}"
