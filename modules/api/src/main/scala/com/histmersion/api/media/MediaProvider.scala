package com.histmersion.api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object MediaProvider:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  val all: List[com.histmersion.api.media.MediaProvider.Id] = List(
    _root_.com.histmersion.api.media.MediaProvider.Id(Source),
    _root_.com.histmersion.api.media.MediaProvider.Id(Commons),
    _root_.com.histmersion.api.media.MediaProvider.Id(Calibre),
    _root_.com.histmersion.api.media.MediaProvider.Id(OpenLibrary),
    _root_.com.histmersion.api.media.MediaProvider.Id(Met),
    _root_.com.histmersion.api.media.MediaProvider.Id(Cleveland),
    _root_.com.histmersion.api.media.MediaProvider.Id(ArtInstitute),
    _root_.com.histmersion.api.media.MediaProvider.Id(LibraryOfCongress),
    _root_.com.histmersion.api.media.MediaProvider.Id(OpenContext),
    _root_.com.histmersion.api.media.MediaProvider.Id(Smithsonian),
    _root_.com.histmersion.api.media.MediaProvider.Id(Europeana)
  )

  val museums: Set[com.histmersion.api.media.MediaProvider.Id] = Set(_root_.com.histmersion.api.media.MediaProvider.Id(Met), _root_.com.histmersion.api.media.MediaProvider.Id(Cleveland), _root_.com.histmersion.api.media.MediaProvider.Id(ArtInstitute))

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  val covers: Set[com.histmersion.api.media.MediaProvider.Id] = Set(_root_.com.histmersion.api.media.MediaProvider.Id(Calibre), _root_.com.histmersion.api.media.MediaProvider.Id(OpenLibrary))

  def title(p: com.histmersion.api.media.MediaProvider.Id): com.histmersion.api.media.MediaProvider.Info.Title = p match
    case Source            => _root_.com.histmersion.api.media.MediaProvider.Info.Title("source figures")
    case Commons           => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Wikimedia Commons")
    case Calibre           => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Calibre library")
    case OpenLibrary       => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Open Library")
    case Met               => _root_.com.histmersion.api.media.MediaProvider.Info.Title("The Metropolitan Museum of Art")
    case Cleveland         => _root_.com.histmersion.api.media.MediaProvider.Info.Title("The Cleveland Museum of Art")
    case ArtInstitute      => _root_.com.histmersion.api.media.MediaProvider.Info.Title("The Art Institute of Chicago")
    case LibraryOfCongress => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Library of Congress")
    case CommonsDepicts    => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Wikimedia Commons (depictions)")
    case OpenContext       => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Open Context")
    case Pleiades          => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Pleiades (linked Commons files)")
    case Smithsonian       => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Smithsonian Institution")
    case Europeana         => _root_.com.histmersion.api.media.MediaProvider.Info.Title("Europeana")
    case other             => _root_.com.histmersion.api.media.MediaProvider.Info.Title((other).value)

  final case class Info(
      name: com.histmersion.api.media.MediaItem.Provider,
      title: Info.Title,
      over: Info.Over,
      description: Info.Description,
      plugin: Info.Plugin,
      builtin: Info.Builtin,
      licences: Info.Licences
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Info:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Over = Over.T
    object Over extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Description = Description.T
    object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Builtin = Builtin.T
    object Builtin extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Licences = Licences.T
    object Licences extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
