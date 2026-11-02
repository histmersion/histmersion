package com.histmersion.server

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Confidence
import com.histmersion.domain.library.Source
import com.histmersion.api.research.Article

/** What a `[[wikilink]]` resolves to on an HTML page: a stored source or a compiled article. */
type LinkTarget = com.histmersion.api.library.Source.Ref | Article.Ref
