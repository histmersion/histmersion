package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import scala.util.matching.Regex

type Detail = Detail.T
object Detail extends RefinedType[String, Pure]

type Citekey2 = Citekey2.T
object Citekey2 extends RefinedType[String, Pure]

final case class Outcome(message: String, error: Option[String], detail: Detail, reason: Problem, note: String, title: String)

object Texts:
  val Known: Citekey2 = Citekey2("known")
  val Interrupted = "interrupted"
  val Quota: Regex = "quota: (.*)".r
  val Digits: Regex = "\\d+".r

/** G5textBranch beyond the layer Problem: other String newtypes that hold a message, and the message fields of a record. */
final class MessageChecks(o: Outcome, e: Exception, d: Detail, citekey: Citekey2, other: Outcome):
  def detailLiteral: Boolean = d.value.contains("stalled") // assert: GuidelineInventory
  def detailPrefix: Boolean = d.value.startsWith("paused") // assert: GuidelineInventory
  def fieldContains: Boolean = o.message.contains("timed out") // assert: GuidelineInventory
  def fieldCompared: Boolean = o.reason.value == "offline" // assert: GuidelineInventory
  def fieldFlipped: Boolean = "offline" != o.note // assert: GuidelineInventory
  def fieldConstant: Boolean = o.message.endsWith(Texts.Interrupted) // assert: GuidelineInventory
  def optional: Boolean = o.error.exists(_.contains("denied")) // assert: GuidelineInventory
  def optionalContains: Boolean = o.error.contains(Texts.Interrupted) // assert: GuidelineInventory
  def throwable: Boolean = e.getMessage.contains("AbortError") // assert: GuidelineInventory
  def throwableString: Boolean = e.toString.startsWith("java.io") // assert: GuidelineInventory
  def regexMatch: Option[String] = Texts.Quota.findFirstIn(o.message) // assert: GuidelineInventory
  def regexCase: Boolean = o.message match // assert: GuidelineInventory
    case Texts.Quota(_) => true
    case _                     => false
  def literalCase: Boolean = o.note match // assert: GuidelineInventory
    case "none" => true
    case _      => false
  def listed: Boolean = List("a", "b").contains(o.detail.value) // assert: GuidelineInventory

  // no finding: a value comparison, not a branch on the text
  def known: Boolean = citekey == Texts.Known
  def knownValue: Boolean = citekey.value == "known" // assert: GuidelineInventory.G44
  def sameMessage: Boolean = o.message == other.message
  def sameDetail: Boolean = o.detail == other.detail
  def titled: Boolean = o.title.contains("history")
  def pathName: Boolean = java.nio.file.Paths.get("a.md").getFileName.toString.endsWith(".md")
  def digits: Option[String] = Texts.Digits.findFirstIn(o.title)
  def shown: String = s"failed: ${o.message}"
  def sized: Int = o.message.length
