package com.histmersion
package api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
object MediaProvider:
  type Id = Id.T
  object Id extends Newtype[String, Pure]

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
  val all: List[Id] = List(
    Id(Source),
    Id(Commons),
    Id(Calibre),
    Id(OpenLibrary),
    Id(Met),
    Id(Cleveland),
    Id(ArtInstitute),
    Id(LibraryOfCongress),
    Id(OpenContext),
    Id(Smithsonian),
    Id(Europeana)
  )

  val museums: Set[Id] = Set(Id(Met), Id(Cleveland), Id(ArtInstitute))

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  val covers: Set[Id] = Set(Id(Calibre), Id(OpenLibrary))

  def title(p: Id): Info.Title = p.value match
    case Source            => Info.Title("source figures")
    case Commons           => Info.Title("Wikimedia Commons")
    case Calibre           => Info.Title("Calibre library")
    case OpenLibrary       => Info.Title("Open Library")
    case Met               => Info.Title("The Metropolitan Museum of Art")
    case Cleveland         => Info.Title("The Cleveland Museum of Art")
    case ArtInstitute      => Info.Title("The Art Institute of Chicago")
    case LibraryOfCongress => Info.Title("Library of Congress")
    case CommonsDepicts    => Info.Title("Wikimedia Commons (depictions)")
    case OpenContext       => Info.Title("Open Context")
    case Pleiades          => Info.Title("Pleiades (linked Commons files)")
    case Smithsonian       => Info.Title("Smithsonian Institution")
    case Europeana         => Info.Title("Europeana")
    case other             => Info.Title(other)

  final case class Info(
      name: MediaItem.Provider,
      title: Info.Title,
      over: Info.Over,
      description: Info.Description,
      plugin: Info.Plugin,
      builtin: Info.Builtin,
      licences: Info.Licences
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Info:
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Over = Over.T
    object Over extends Newtype[String, Pure]
    type Description = Description.T
    object Description extends Newtype[String, Pure]
    type Plugin = Plugin.T
    object Plugin extends Newtype[String, Pure]
    type Builtin = Builtin.T
    object Builtin extends Newtype[Boolean, Pure]
    type Licences = Licences.T
    object Licences extends Newtype[String, Pure]
