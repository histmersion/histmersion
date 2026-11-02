package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.{Confidence, SourceStatus}
import com.histmersion.api.library.SourceRef
import com.histmersion.api.research.ArticleRef

/** What a `[[wikilink]]` resolves to on an HTML page: a stored source or a compiled article. */
type LinkTarget = SourceRef | ArticleRef
