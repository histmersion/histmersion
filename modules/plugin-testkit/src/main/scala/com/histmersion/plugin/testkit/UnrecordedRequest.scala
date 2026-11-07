package com.histmersion.plugin.testkit

import com.histmersion.pluginhost.HttpTransport

import java.nio.file.Path

/** A request a [[HttpMode.Replay]] had no recording of: the test fails with it, naming the URLs. */
final class UnrecordedRequest(urls: List[HttpTransport.Url], dir: Path)
    extends AssertionError(
      s"no recording in $dir of: ${urls.distinct.map(_.value).mkString(", ")} — record the case again " +
        s"(${HttpMode.ModeVariable}=record), or the plugin now asks for something it did not ask for before"
    )
