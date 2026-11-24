package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Confidence
import com.histmersion.domain.library.Source
import com.histmersion.api.research.Article
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** What a `[[wikilink]]` resolves to on an HTML page: a stored source or a compiled article. */
@tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
@movesTo(FeaturePlace.ResearchArticles)
type LinkTarget = com.histmersion.api.library.Source.Ref | Article.Ref
