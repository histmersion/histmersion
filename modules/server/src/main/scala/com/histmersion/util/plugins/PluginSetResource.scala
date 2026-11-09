package com.histmersion
package util.plugins
import com.histmersion.util.problem.Problem

import com.histmersion.util.plugins.PluginKit
import com.histmersion.api.plugins.PluginSet

import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.jar.{JarEntry, JarOutputStream}

/** The plugin set inside a binary (docs/research-plugins.md §7.3): `META-INF/histmersion/plugin-set.json` in a small
  * generated jar that a relink (and the JVM launcher) puts on the class path. The jar carries its own reachability
  * metadata registering that resource, as any plugin jar does, so the native image keeps it. A stock image has none:
  * first-party plugins only.
  */
object PluginSetResource:
  private val MetadataPath = "META-INF/native-image/com.histmersion/plugin-set/reachability-metadata.json"
  private val Metadata = s"""{\n  "resources": [ { "glob": "${PluginSet.Resource}" } ]\n}\n"""

  /** Entries get one fixed time, so the same set always makes the same jar bytes. */
  private val EntryTime = 315532800000L // 1980-01-01, the earliest a zip entry can carry

  /** The set on `loader`'s class path; `Right(None)` when there is none, `Left` when it does not parse. */
  def load(loader: ClassLoader): Either[Problem, Option[PluginSet]] =
    Option(loader.getResourceAsStream(PluginSet.Resource)) match
      case None     => Right(None)
      case Some(in) =>
        try PluginKit.parseSet(String(in.readAllBytes(), UTF_8)).map(Some(_))
        finally in.close()

  /** The plugin-set jar written to `out`, which the caller opened and closes (the relink tool writes it beside the file
    * it replaces, then renames it).
    */
  def writeJar(out: OutputStream, set: PluginSet): Unit =
    val jar = JarOutputStream(out)
    try
      def entry(name: String, text: String): Unit =
        val e = JarEntry(name)
        e.setTime(EntryTime)
        jar.putNextEntry(e)
        jar.write(text.getBytes(UTF_8))
        jar.closeEntry()
      entry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nCreated-By: Histmersion relink\r\n\r\n")
      entry(PluginSet.Resource, PluginKit.renderSet(set) + "\n")
      entry(MetadataPath, Metadata)
    finally jar.finish()
