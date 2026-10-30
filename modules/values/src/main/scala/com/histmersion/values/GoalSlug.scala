package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A goal's slug: the article slug's rule, but its own type, so a goal never passes for an article. */
type GoalSlug = GoalSlug.T

object GoalSlug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]
