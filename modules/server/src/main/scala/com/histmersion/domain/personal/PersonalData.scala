package com.histmersion.domain.personal

import com.histmersion.util.data.{DataContribution, DataOwner, OwnedTable}
import com.histmersion.util.data.diagnostics.DataFigures
import com.augustnagro.magnum.DbCon

/** What personal says about the data it owns: a person's bookmarks, notes, read marks and the windows' seen marks. A
  * bundle carries none of it (a person's marks stay on their collection) and About counts none.
  */
object PersonalData extends DataContribution:
  val owner: DataOwner = DataOwner("personal")

  val tables: List[OwnedTable] = OwnedTable.all("bookmarks", "notes", "read_marks", "ui_seen")

  def figures(using DbCon): DataFigures = DataFigures.empty
