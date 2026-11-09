package com.histmersion
package util.problem

// G60fromString: the closed Problem's temporary text case counts; its typed cases do not
sealed trait Problem

object Problem:
  final case class Unclassified(text: String) extends Problem
  final case class Missing(field: Int) extends Problem

  val typed: Problem = Missing(3) // no finding: a typed case
  val literal: Problem = Unclassified("cannot open") // assert: GuidelineInventory.G60fromString
  def spliced(n: Int): Problem = Unclassified(s"item $n") // assert: GuidelineInventory.G60fromString
  val applied: Problem = Unclassified.apply("x" + "y") // assert: GuidelineInventory.G60fromString
  def read(text: String): Option[Problem] = text match
    case "x"                        => None
    case other if other.length > 99 => Some(Unclassified(other)) // assert: GuidelineInventory.G60fromString
    case _                          => None

  def matched(p: Problem): Boolean = p match
    case Unclassified(_) => true // no finding: a pattern
    case _               => false
