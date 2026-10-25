package com.histmersion.domain

import com.histmersion.values.PlaceKind

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The committed gazetteer seed set (`db/places`) as a list of files in IMPORT ORDER — shared by the
  * CLI's `histmersion places seed` (which reads the files on the host) and the server's first-run
  * import (#720, which reads the copy shipped in the bundle), so both walk the directory the same way.
  * It only lists a directory; reading and importing is the caller's. */
object PlaceSeed:

  /** Every JSON/GeoJSON file under a seed directory, in import order: the top-level files by name
    * first (Natural Earth defines the `continent_*` ids everything else hangs off), then each
    * subdirectory by name. The fallback `kind` for a file comes from its directory — `polities/` →
    * `polity` — and a feature that declares its own `kind` always wins over it. */
  def files(dir: Path): List[(Path, Option[String])] =
    def listing(d: Path): List[Path] =
      val st = Files.list(d)
      try st.iterator.asScala.toList.sortBy(_.getFileName.toString) finally st.close()
    def isSeed(f: Path) =
      val n = f.getFileName.toString
      Files.isRegularFile(f) && (n.endsWith(".json") || n.endsWith(".geojson"))
    /** `polities` → `polity`, `sites` → `site`, `regions` → `region`; anything else → no override. */
    def dirKind(name: String) =
      val singular = if name.endsWith("ies") then name.dropRight(3) + "y"
        else if name.endsWith("s") then name.dropRight(1) else name
      PlaceKind.parse(singular)
    val entries = listing(dir)
    entries.filter(isSeed).map(_ -> None) ++
      entries.filter(Files.isDirectory(_)).flatMap { sub =>
        listing(sub).filter(isSeed).map(_ -> dirKind(sub.getFileName.toString))
      }
