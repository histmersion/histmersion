package com.histmersion
package api

import sttp.tapir.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
@movesTo(FeaturePlace.Kernel)
final case class OffsetPage(total: RowCount, offset: Offset, shown: RowCount):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def next: Option[Offset] = (Option
    .when(shown.value > 0 && offset.value + shown.value < total.value)(offset.value + shown.value))
    .map(Offset.apply)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def isAll: Boolean = (offset.value == 0 && shown >= total)

object OffsetPage:
  type FullSetMax = FullSetMaxValue
  type FullSetMaxValue = FullSetMaxValue.T
  object FullSetMaxValue extends Newtype[Int, Pure]
  type Markdown = Markdown.T
  object Markdown extends Newtype[String, Pure]

  val FullSetMax: FullSetMax = FullSetMaxValue(500)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def header(p: OffsetPage, noun: String): String =
    if p.isAll then (s"### ${math.max(p.total.value, p.shown.value)} $noun")
    else (s"### ${p.shown.value} of ${p.total.value} $noun")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def footer(p: OffsetPage): String =
    if ((p.isAll) == (true)) || p.shown.value == 0 then ("")
    else
      val next = p.next.fold("")(n => s" — next: offset=$n")
      val full = if p.total.value <= FullSetMax.value then s" — full set: limit=${p.total.value}" else ""
      (s"\n\n_showing ${p.offset.value + 1}–${p.offset.value + p.shown.value} of ${p.total.value}$next${full}_")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown[A](p: OffsetPage, noun: String, empty: String, items: List[A])(line: A => String): Markdown =
    if items.isEmpty then
      p.offset.value match
        case 0 => Markdown((empty))
        case o => Markdown(s"$empty\n\n_offset=$o is past the end: there are ${p.total.value}_")
    else Markdown(s"${header(p, ((noun)))}\n\n${items.map(line).mkString("\n")}${footer(p)}")
