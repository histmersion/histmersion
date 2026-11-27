package com.histmersion
package api.personal

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Title
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class ReadOpened(kind: ItemKind, id: ItemKey, title: Option[Title] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
