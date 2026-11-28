package com.histmersion
package domain.research.articles.lint

import com.histmersion.api.checks.FormatIssue

final case class Offence(issue: FormatIssue, edits: List[Edit])
