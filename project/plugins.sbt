// NOTE: sbt-native-image (and most native-image plugins) are not yet published for sbt 2.0
// (plugins there are Scala-3 `_sbt2_3` artifacts). Native images are built by a small custom
// `nativeImage` task in build.sbt that shells out to GraalVM's `native-image` directly.
