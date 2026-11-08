package com.histmersion.api.research

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

enum Kind:
  case Concept, Myth

object Kind:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]
  // A1 UNCHANGED: the owner is the enclosing object itself (Scala 3: "cannot be exported because it is already a member")
  type FromStringS = com.histmersion.api.research.Kind.Wire
  val FromStringS: _root_.com.histmersion.api.research.Kind.Wire.type = _root_.com.histmersion.api.research.Kind.Wire

object Coverage:
  object Entry:
    type Reason = Reason.T
    object Reason extends RefinedType[String, Pure]
    type Note = Note.T
    object Note extends RefinedType[String, Pure]

object Article:
  type Slug = Slug.T
  object Slug extends RefinedType[String, Pure]
  // A2 REWRITE: renaming pair from another object
  export com.histmersion.api.research.Kind.{Wire as KindValue}
  type Body = Body.T
  object Body extends RefinedType[String, Pure]
  // A3 REWRITE: two consecutive pairs from one owner share one export, the first keeps its name
  export com.histmersion.api.research.Coverage.Entry.{Reason, Note as NoteValue}
  // A4 UNCHANGED: a comment between the halves
  type Second = com.histmersion.api.research.Coverage.Entry.Note
  // the value half
  val Second: _root_.com.histmersion.api.research.Coverage.Entry.Note.type = _root_.com.histmersion.api.research.Coverage.Entry.Note
  // A5 UNCHANGED: a modifier
  private type Third = com.histmersion.api.research.Coverage.Entry.Note
  private val Third: _root_.com.histmersion.api.research.Coverage.Entry.Note.type = _root_.com.histmersion.api.research.Coverage.Entry.Note
  // A6 UNCHANGED: the val names something else
  type Fourth = com.histmersion.api.research.Coverage.Entry.Note
  val Fourth: _root_.com.histmersion.api.research.Coverage.Entry.Reason.type = _root_.com.histmersion.api.research.Coverage.Entry.Reason

  def third: Third = Third("x")

// A7 UNCHANGED: the uses, as type, constructor and value
object Uses:
  def kind(k: Article.KindValue): Article.KindValue = Article.KindValue(k.value + "!")
  def reason: Article.Reason = Article.Reason("why")
  def note: Article.NoteValue = Article.NoteValue.applyUnsafe("n")
  def same: Kind.FromStringS = Kind.FromStringS("concept")
  def obj: Article.KindValue.type = Article.KindValue
