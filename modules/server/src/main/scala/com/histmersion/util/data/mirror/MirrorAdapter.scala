package com.histmersion.util.data.mirror

import com.histmersion.api.ApiError
import com.histmersion.util.data.{DataOwner, TablesAdapter}

/** Today's mirror notes, registered by owner until each owner registers its own: the library's source notes in
  * `sources/`, research's article notes by their kind, rendered by today's per-kind code the composition root hands
  * over.
  */
object MirrorAdapter:
  val SourceNotes: MirrorRecords = MirrorRecords("sources")
  val ArticleNotes: MirrorRecords = MirrorRecords("articles")

  final private class Today(
      val owner: DataOwner,
      val records: MirrorRecords,
      val placement: MirrorPlacement,
      render: TransitionNotes => String => Either[ApiError, String]
  ) extends MirrorExport:
    def note(id: String)(using notes: TransitionNotes): Either[ApiError, String] = render(notes)(id)

  def of(owner: DataOwner): List[MirrorExport] = owner match
    case TablesAdapter.ResearchOwner => List(Today(owner, ArticleNotes, MirrorPlacement.ArticleFiles, _.articleNote))
    case _                           => Nil
