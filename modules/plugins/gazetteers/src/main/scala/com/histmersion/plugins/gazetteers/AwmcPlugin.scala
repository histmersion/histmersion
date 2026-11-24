package com.histmersion
package plugins.gazetteers

import com.histmersion.lint.{LintRule, tolerated}
/** `gazetteer-awmc`: `histmersion places fetch awmc` — the Ancient World Mapping Center's regional names. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class AwmcPlugin extends GazetteerPlugin("gazetteer-awmc", "awmc", Awmc)
