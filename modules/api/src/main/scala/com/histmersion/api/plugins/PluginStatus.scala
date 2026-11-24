package com.histmersion
package api.plugins

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class PluginStatus(
    id: PluginStatus.Id,
    version: PluginStatus.Version,
    spiVersion: PluginStatus.SpiVersion,
    enabled: PluginStatus.Enabled,
    state: PluginState.Wire,
    provides: List[PluginStatus.Provides] = Nil,
    settings: List[PluginSetting] = Nil,
    warnings: List[PluginStatus.Warnings] = Nil,
    errors: List[Problem] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PluginStatus:
  extension (status: PluginStatus)
    /** Every reason the plugin failed or was refused for, in order, as one line. */
    def reasons: String =
      if status.errors.isEmpty then "no reason given" else status.errors.map(_.sentence).mkString("; ")

  export PluginDescription.{Id, SpiVersion, Version}
  type Enabled = Enabled.T
  object Enabled extends Newtype[Boolean, Pure]
  export PluginDescription.Provides
  type Warnings = Warnings.T
  object Warnings extends Newtype[String, Pure]
