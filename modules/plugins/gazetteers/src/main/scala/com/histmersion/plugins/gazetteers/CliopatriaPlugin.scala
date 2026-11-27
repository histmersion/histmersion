package com.histmersion
package plugins.gazetteers

import com.histmersion.lint.{tolerated, LintRule}

/** `gazetteer-cliopatria`: `histmersion places fetch cliopatria` — Seshat Cliopatria's polity outlines. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class CliopatriaPlugin extends GazetteerPlugin("gazetteer-cliopatria", "cliopatria", Cliopatria)
