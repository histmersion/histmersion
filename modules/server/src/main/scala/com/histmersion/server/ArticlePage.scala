package com.histmersion
package server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.checks.FormatIssue
import com.histmersion.api.research.{Backlink, CitedSource, Coverage}
import com.histmersion.api.research.Article

/** Everything an `/article/{slug}` page shows, typed. */
final case class ArticlePage(
    article: Article.Row,
    backlinks: List[Backlink],
    cited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    /** The coverage of its recorded filter (#958): the generated Not used list and Method note. */
    coverage: Option[Coverage] = None,
    /** Its review state, the goals linking it and its review history. */
    review: Option[Article.ReviewView] = None
)
