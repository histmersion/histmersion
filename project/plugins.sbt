// NOTE: sbt-native-image (and most native-image plugins) are not yet published for sbt 2.0
// (plugins there are Scala-3 `_sbt2_3` artifacts). Native images are built by a small custom
// `nativeImage` task in build.sbt that shells out to GraalVM's `native-image` directly.

addSbtPlugin("org.scala-js"        % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("org.portable-scala"  % "sbt-scalajs-crossproject" % "1.4.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.6.2")

// The meta-build's own sbt 2 disk cache: this checkout's target/sbt-cache, as for the build itself
// (build.sbt, "one per checkout"), never the machine-wide ~/Library/Caches/sbt/v2.
Global / localCacheDirectory := appConfiguration.value.baseDirectory.getCanonicalFile / "target" / "sbt-cache"
