package com.histmersion.api.media

import com.histmersion.api.library.Source
import com.histmersion.api.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
object MediaProvider:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Museums = Museums.T
  object Museums extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Covers = Covers.T
  object Covers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleP = TitleP.T
  object TitleP extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  val all: List[com.histmersion.api.media.MediaProvider.All] = List(
    _root_.com.histmersion.api.media.MediaProvider.All(Source),
    _root_.com.histmersion.api.media.MediaProvider.All(Commons),
    _root_.com.histmersion.api.media.MediaProvider.All(Calibre),
    _root_.com.histmersion.api.media.MediaProvider.All(OpenLibrary),
    _root_.com.histmersion.api.media.MediaProvider.All(Met),
    _root_.com.histmersion.api.media.MediaProvider.All(Cleveland),
    _root_.com.histmersion.api.media.MediaProvider.All(ArtInstitute),
    _root_.com.histmersion.api.media.MediaProvider.All(LibraryOfCongress),
    _root_.com.histmersion.api.media.MediaProvider.All(OpenContext),
    _root_.com.histmersion.api.media.MediaProvider.All(Smithsonian),
    _root_.com.histmersion.api.media.MediaProvider.All(Europeana)
  )

  val museums: Set[com.histmersion.api.media.MediaProvider.Museums] = Set(_root_.com.histmersion.api.media.MediaProvider.Museums(Met), _root_.com.histmersion.api.media.MediaProvider.Museums(Cleveland), _root_.com.histmersion.api.media.MediaProvider.Museums(ArtInstitute))

  /** Covers carry the publisher's rights, no reuse licence: shown in the local UI only, never carried in a bundle. */
  val covers: Set[com.histmersion.api.media.MediaProvider.Covers] = Set(_root_.com.histmersion.api.media.MediaProvider.Covers(Calibre), _root_.com.histmersion.api.media.MediaProvider.Covers(OpenLibrary))

  def title(p: com.histmersion.api.media.MediaProvider.TitleP): com.histmersion.api.media.MediaProvider.TitleResult = p match
    case Source            => _root_.com.histmersion.api.media.MediaProvider.TitleResult("source figures")
    case Commons           => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Wikimedia Commons")
    case Calibre           => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Calibre library")
    case OpenLibrary       => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Open Library")
    case Met               => _root_.com.histmersion.api.media.MediaProvider.TitleResult("The Metropolitan Museum of Art")
    case Cleveland         => _root_.com.histmersion.api.media.MediaProvider.TitleResult("The Cleveland Museum of Art")
    case ArtInstitute      => _root_.com.histmersion.api.media.MediaProvider.TitleResult("The Art Institute of Chicago")
    case LibraryOfCongress => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Library of Congress")
    case CommonsDepicts    => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Wikimedia Commons (depictions)")
    case OpenContext       => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Open Context")
    case Pleiades          => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Pleiades (linked Commons files)")
    case Smithsonian       => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Smithsonian Institution")
    case Europeana         => _root_.com.histmersion.api.media.MediaProvider.TitleResult("Europeana")
    case other             => _root_.com.histmersion.api.media.MediaProvider.TitleResult((other).value)

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
