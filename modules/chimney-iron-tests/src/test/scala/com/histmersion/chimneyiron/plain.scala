package com.histmersion
package chimneyiron.plain

import com.histmersion.lint.{tolerated, LintRule}
final case class Score(value: Int) extends AnyVal
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class ScoreView(value: Int) extends AnyVal

final case class Entry(name: String, score: Score, aliases: List[String], weight: Option[Double])
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class EntryView(name: String, score: ScoreView, aliases: Vector[String], weight: Option[Double])
