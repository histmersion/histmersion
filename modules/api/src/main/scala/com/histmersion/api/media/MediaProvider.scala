package com.histmersion.api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object MediaProvider:
  val Source = "source"
  val Commons = "wikimedia-commons"

  val Calibre = "calibre"

  val OpenLibrary = "open-library"

  val Met = "met-museum"
  val Cleveland = "cleveland-museum"
  val ArtInstitute = "art-institute-chicago"

  val LibraryOfCongress = "library-of-congress"

  val CommonsDepicts = "wikimedia-commons-depicts"

  val OpenContext = "open-context"

  val Pleiades = "pleiades"

  val Smithsonian = "smithsonian"

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

  val museums: Set[String] = Set(Met, Cleveland, ArtInstitute)

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  val covers: Set[String] = Set(Calibre, OpenLibrary)

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

  final case class Info(
      name: String,
      title: String,
      over: String,
      description: String,
      plugin: String,
      builtin: Boolean,
      licences: String
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
