package com.histmersion
package api.automation.queue

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Goal

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class GoalRef(slug: Goal.Slug, title: Goal.Title) derives JsonCodec, KindlingsSchema, api.json.Schema
