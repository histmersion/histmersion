package com.histmersion
package util.identity

import com.augustnagro.magnum.Frag

/** An owner's alias table: the old key a rename left (`alias`) and the key it stands for now (`target`), each the
  * owner's constant SQL name (`sql"article_aliases"`), nested into the statements that read it.
  */
final case class AliasColumns(table: Frag, alias: Frag, target: Frag)
