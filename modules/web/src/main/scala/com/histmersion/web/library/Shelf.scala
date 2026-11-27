package com.histmersion
package web.library

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A shelf of My library: everything the filters select, the person's bookmarks, their notes, what they have not marked
  * read, and what they opened lately.
  */
enum Shelf(val wire: Shelf.Wire, val label: Shelf.Label) derives CanEqual:
  case Everything extends Shelf(Shelf.Wire("all"), Shelf.Label("Everything"))
  case Bookmarks extends Shelf(Shelf.Wire("bookmarks"), Shelf.Label("Bookmarks"))
  case Notes extends Shelf(Shelf.Wire("notes"), Shelf.Label("My notes"))
  case Unread extends Shelf(Shelf.Wire("unread"), Shelf.Label("Not read yet"))
  case Recent extends Shelf(Shelf.Wire("recent"), Shelf.Label("Recently opened"))

object Shelf:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Shelf] = values.find(_.wire.value == s.value.trim.toLowerCase)
