package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import com.histmersion.lint.{FeaturePlace, movesTo}

/** Research's settings among `agents.managers.*`: the review manager. */
@movesTo(FeaturePlace.ResearchWorkflows)
final case class ResearchManagersConfig(review: ReviewManagerConfig) derives ConfigReader
