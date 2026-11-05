package com.histmersion.domain.catalogue.places

/** The committed gazetteer seed set (`db/places`) as a list of files in IMPORT ORDER — shared by the CLI's
  * `histmersion places seed` (which reads the files on the host) and the server's first-run import (#720, which reads
  * the copy shipped in the bundle), so both walk the directory the same way. It only orders what a directory lists;
  * listing and reading are the caller's (a path on the host, a store in the server).
  */
import com.histmersion.domain.catalogue.Place
object PlaceSeed:

  /** One entry of a seed directory: its name, whether it is a directory, and the caller's handle to it. */
  final case class Entry[K](name: Entry.Name, directory: Entry.Directory, at: K)

  object Entry:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Directory = Directory.T
    object Directory extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Every JSON/GeoJSON file of a seed directory, in import order: the top-level files by name first (Natural Earth
    * defines the `continent_*` ids everything else hangs off), then each subdirectory by name. The fallback `kind` for
    * a file comes from its directory — `polities/` → `polity` — and a feature that declares its own `kind` always wins
    * over it. `top` is what the seed directory holds, `list` what a subdirectory holds.
    */
  def files[K](top: List[Entry[K]], list: K => List[Entry[K]]): List[(K, Option[String])] =
    def isSeed(e: Entry[K]) = !e.directory && (e.name.endsWith(".json") || e.name.endsWith(".geojson"))

    /** `polities` → `polity`, `sites` → `site`, `regions` → `region`; anything else → no override. */
    def dirKind(name: String) =
      val singular = if name.endsWith("ies") then name.dropRight(3) + "y"
      else if name.endsWith("s") then name.dropRight(1)
      else name
      Place.Kind.parse(singular)
    val entries = top.sortBy(_.name)
    entries.filter(isSeed).map(_.at -> None) ++
      entries.filter(_.directory).flatMap { sub =>
        list(sub.at).sortBy(_.name).filter(isSeed).map(_.at -> dirKind(sub.name))
      }
