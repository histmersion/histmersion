package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

type Note = Note.T
object Note extends RefinedType[String, Pure]:
  /** The note a value of any newtype holds: unwrapped in the companion, the place G44 allows. */
  def of[T](value: T)(using RefinedType.Mirror[T]): Note = Note(s"$value") // no finding: inside the companion

type Shelf = Shelf.T
object Shelf extends RefinedType[String, Match["[a-z]+"]]

object Failures:
  def plain(name: String): Problem = Problem("cannot open") // assert: GuidelineInventory.G60fromString (no G44: a literal)
  def interpolated(name: String): Problem = Problem(s"cannot open $name") // assert: GuidelineInventory
  def formatted(n: Int): Problem = Problem(f"cannot open $n%03d") // assert: GuidelineInventory
  def other(name: String): Note = Note(s"seen $name") // assert: GuidelineInventory.G44
  def ofNewtype(label: String, c: Citekey): Note = Note(s"$c") // assert: GuidelineInventory.G44
  def literalSplices: Note = Note(s"seen ${"x"} ${3}") // no finding: every splice a literal
  def noSplice: Note = Note(s"seen") // no finding: nothing spliced
  def throughOf(label: String, c: Citekey): Note = Note.of(c) // no finding: the companion unwraps
  def spliced(c: Citekey): Problem = Problem(s"no source ${c.value}") // assert: GuidelineInventory.G44
  def refined(name: String): Shelf = Shelf.assume(s"top-$name") // assert: GuidelineInventory.G44
  def computed(name: String): Problem = Problem.assume(name + "!") // assert: GuidelineInventory.G44
  def unsafe(name: String): Problem = Problem.applyUnsafe(s"cannot open $name") // assert: GuidelineInventory.G44

// G5problem: a failure message newtype other than the layer's one Problem
type ReviewProblem = ReviewProblem.T
object ReviewProblem extends RefinedType[String, Pure] // assert: GuidelineInventory.G5problem

object Check:
  type Why = Why.T
  object Why extends RefinedType[String, Pure] // assert: GuidelineInventory.G5problem

type ErrorCount = ErrorCount.T
object ErrorCount extends RefinedType[Int, Pure] // no finding: not over a String

type Reasoning = Reasoning.T
object Reasoning extends RefinedType[String, Pure] // no finding: the word is not the name's end

enum Refusal:
  case Missing, Stale // no finding: an enum, not a newtype

// G55positional: a mechanical name
type LookupResult = LookupResult.T
object LookupResult extends RefinedType[String, Pure] // assert: GuidelineInventory.G55positional

type Round1Result = Seq[Int] // assert: GuidelineInventory.G55positional
type MergeArg2 = Seq[Int] // assert: GuidelineInventory.G55positional
type HeaderPart1 = String // assert: GuidelineInventory.G55positional

type Results = Seq[Int] // no finding: plural of a concept
type Resulting = Seq[Int] // no finding: not a trailing Result
type Result = Seq[Int] // no finding: no concept before the word
type Heading = Heading.T // no finding: the alias of a newtype is read through its object
object Heading extends RefinedType[String, Pure]
