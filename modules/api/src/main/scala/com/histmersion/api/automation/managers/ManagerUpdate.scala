package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ManagerUpdate(config: Option[ManagerConfig] = None, cron: Option[ManagerUpdate.Cron] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ManagerUpdate:
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
