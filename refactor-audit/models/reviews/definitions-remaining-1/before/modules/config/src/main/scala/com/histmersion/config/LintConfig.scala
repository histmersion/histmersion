package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion lint`: how much of each rule's offending set is LISTED (the count is always complete) and how many
  * citekey prefix candidates a dangling edge target is offered.
  */
final case class LintConfig(maxItems: Int, suggestions: Int) derives ConfigReader
