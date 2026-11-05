package com.histmersion.domain.personal

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** What an item was called when the person marked it, kept for an item the collection no longer (or never) holds. */
type TargetLabel = TargetLabel.T

object TargetLabel extends RefinedType[String, Pure]:
  /** The most characters a name keeps. */
  val MaxChars = 300

  /** A name tidied (spaces collapsed, bounded); none when empty. */
  def tidy(raw: String): Option[TargetLabel] =
    Option(raw).map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty).map(t => TargetLabel(t.take(MaxChars)))

  def tidy(raw: Option[String]): Option[TargetLabel] = raw.flatMap(tidy)
