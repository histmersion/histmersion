package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** Everything an `/article/{slug}` page shows, typed. */
final case class ArticlePage(
    article: ArticleRow,
    backlinks: List[Backlink],
    cited: List[CitedSource],
    formatting: List[FormatIssue] = Nil,
    /** The coverage of its recorded filter (#958): the generated Not used list and Method note. */
    coverage: Option[Coverage] = None,
    /** Its review state, the goals linking it and its review history. */
    review: Option[ArticleReviewView] = None
)
