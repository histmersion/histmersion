package com.histmersion
package plugins.gazetteers

import com.histmersion.lint.{tolerated, LintRule}

/** `gazetteer-chronontology`: `histmersion places fetch chronontology` — ChronOntology's named periods. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class ChronOntologyPlugin extends GazetteerPlugin("gazetteer-chronontology", "chronontology", ChronOntology)
