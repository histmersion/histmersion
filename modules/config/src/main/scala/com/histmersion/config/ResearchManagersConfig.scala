package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Research's settings among `agents.managers.*`: the review manager. */
final case class ResearchManagersConfig(review: ReviewManagerConfig) derives ConfigReader
