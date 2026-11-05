package com.histmersion.plugin.testkit

import java.nio.file.Path

/** A request a [[HttpMode.Replay]] had no recording of: the test fails with it, naming the URLs. */
final class UnrecordedRequest(urls: List[UnrecordedRequest.Urls], dir: Path)
    extends AssertionError(
      s"no recording in $dir of: ${urls.distinct.mkString(", ")} — record the case again " +
        s"(${HttpMode.ModeVariable}=record), or the plugin now asks for something it did not ask for before"
    )

object UnrecordedRequest:
  type Urls = Urls.T
  object Urls extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
