package com.histmersion.chimneyiron.plain

final case class Score(value: Int) extends AnyVal
final case class ScoreView(value: Int) extends AnyVal

final case class Entry(name: String, score: Score, aliases: List[String], weight: Option[Double])
final case class EntryView(name: String, score: ScoreView, aliases: Vector[String], weight: Option[Double])
