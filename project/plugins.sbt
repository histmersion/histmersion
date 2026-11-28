// NOTE: sbt-native-image (and most native-image plugins) are not yet published for sbt 2.0
// (plugins there are Scala-3 `_sbt2_3` artifacts). Native images are built by a small custom
// `nativeImage` task (project/NativeImage.scala) that shells out to GraalVM's `native-image` directly.

// sbt-kubuszok (the owner's build convention, #69): git versioning, publishing, `ci-release`, scalafmt on compile off CI,
// and the bundled plugins this build uses, sbt-scalajs 1.23.0 and sbt-scalafmt 2.6.2 (with sbt-git, sbt-scoverage,
// sbt-commandmatrix, sbt-pgp, sbt-mima-plugin, sbt-ide-settings, sbt-scala-native). 0.2.6 is the version for sbt 2.1
// (project/build.properties): the two are bumped together. Never re-add a bundled plugin here.
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.6")

// The meta-build's own sbt 2 disk cache: this checkout's target/sbt-cache, as for the build itself
// (build.sbt, "one per checkout"), never the machine-wide ~/Library/Caches/sbt/v2.
Global / localCacheDirectory := appConfiguration.value.baseDirectory.getCanonicalFile / "target" / "sbt-cache"
