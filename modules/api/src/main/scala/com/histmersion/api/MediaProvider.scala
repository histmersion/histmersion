package com.histmersion.api

// ── media from outside providers (#1446) ────────────────────────────────────────────────────────

/** Where a picture comes from. A source's figures are `source`; every outside provider has its own name, which is also
  * its config block (`media.commons`) and the media layer's provider filter.
  */
object MediaProvider:
  val Source = "source"
  val Commons = "wikimedia-commons"

  /** A book's cover from the user's own Calibre library (read host-side by the CLI, uploaded). */
  val Calibre = "calibre"

  /** A book's cover from the Open Library Covers API, by ISBN or OCLC number. */
  val OpenLibrary = "open-library"

  /** Open Access objects of museum collections (CC0), tied to a place by find-spot and dates. */
  val Met = "met-museum"
  val Cleveland = "cleveland-museum"
  val ArtInstitute = "art-institute-chicago"

  /** Public-domain historical maps of the Library of Congress's Geography and Map Division. */
  val LibraryOfCongress = "library-of-congress"

  /** Not an asset provider: what the depictions import (Commons files that depict an event) records its checks and
    * reports under — its pictures are `wikimedia-commons` assets.
    */
  val CommonsDepicts = "wikimedia-commons-depicts"

  /** Open Context's media (photographs and drawings of excavated sites and finds), each under its own record's Creative
    * Commons licence, tied to a place by a shared gazetteer id or its site's name.
    */
  val OpenContext = "open-context"

  /** Not an asset provider: what the Pleiades import (Commons files a Pleiades place record links) records its checks
    * and reports under — its pictures are `wikimedia-commons` assets.
    */
  val Pleiades = "pleiades"

  /** Smithsonian Open Access objects whose image is CC0 (an api.data.gov key; off by default). */
  val Smithsonian = "smithsonian"

  /** Europeana's openly licensed images (CC0, PDM, CC BY, CC BY-SA; a personal key; off by default). */
  val Europeana = "europeana"
  val all: List[String] = List(
    Source,
    Commons,
    Calibre,
    OpenLibrary,
    Met,
    Cleveland,
    ArtInstitute,
    LibraryOfCongress,
    OpenContext,
    Smithsonian,
    Europeana
  )

  /** The museum collections: an object's picture, tied to a place by find-spot and dates. */
  val museums: Set[String] = Set(Met, Cleveland, ArtInstitute)

  /** Covers: bibliographic pictures of the user's own books, shown in the local UI only. They carry no reuse licence
    * (the publisher's), so the licence lint leaves them alone, and a bundle never carries them (it carries a source's
    * figures only).
    */
  val covers: Set[String] = Set(Calibre, OpenLibrary)

  /** What to call a provider on a card or in a menu. */
  def title(p: String): String = p match
    case Source            => "source figures"
    case Commons           => "Wikimedia Commons"
    case Calibre           => "Calibre library"
    case OpenLibrary       => "Open Library"
    case Met               => "The Metropolitan Museum of Art"
    case Cleveland         => "The Cleveland Museum of Art"
    case ArtInstitute      => "The Art Institute of Chicago"
    case LibraryOfCongress => "Library of Congress"
    case CommonsDepicts    => "Wikimedia Commons (depictions)"
    case OpenContext       => "Open Context"
    case Pleiades          => "Pleiades (linked Commons files)"
    case Smithsonian       => "Smithsonian Institution"
    case Europeana         => "Europeana"
    case other             => other
