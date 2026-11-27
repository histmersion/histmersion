package com.histmersion
package plugin.testkit

import com.histmersion.pluginhost.HttpTransport

import java.nio.file.Path
import com.histmersion.lint.{tolerated, LintRule}

/** A request a [[HttpMode.Replay]] had no recording of: the test fails with it, naming the URLs. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
final class UnrecordedRequest(urls: List[HttpTransport.Url], dir: Path)
    extends AssertionError(
      s"no recording in $dir of: ${urls.distinct.map(_.value).mkString(", ")} — record the case again " +
        s"(${HttpMode.ModeVariable}=record), or the plugin now asks for something it did not ask for before"
    )
