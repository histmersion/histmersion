// Branch contracts3-snapshots only: the Sonatype Central snapshots repository, so the build resolves the unreleased
// Hearth and Kindlings snapshots it verifies (project/Dependencies.scala). Never merged into a release branch.
ThisBuild / resolvers += "Sonatype Central snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
