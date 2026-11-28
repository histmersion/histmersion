package com.histmersion
package api.automation.modelchains

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class ChainSet(models: List[ChainSet.Models]) derives JsonCodec, KindlingsSchema, api.json.Schema

object ChainSet:
  export ChainEntry.Model as Models
