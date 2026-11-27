package com.histmersion
package fixture

import io.github.iltotore.iron.constraint.any.Pure

type Title = Title.T
object Title extends Newtype[String, Pure]

object Shared:
  type Label = Label.T
  object Label extends Newtype[String, Pure]

object Row:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    def blank: Note = Note("")

object Uses:
  def labelled(l: Row.Label): Shared.Label = Shared.Label(l.value)
  def shown(t: Row.Title): Title = Title(t.value)
  def noted(n: Row.Note): Title = Title(n.value)
