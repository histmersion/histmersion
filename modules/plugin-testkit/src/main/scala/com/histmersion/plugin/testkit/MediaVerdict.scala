package com.histmersion
package plugin.testkit

import com.histmersion.domain.media.imports.MediaRecord
import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginMedia

/** How the server judges one record a media collection answered, before it ties it to a place.
  *
  * @param key
  *   the record's key, or the reason the collection gave for an unreadable one
  * @param licence
  *   the licence as the SERVER reads the statement (`CC-BY-4.0`), when it can
  * @param refused
  *   why the server would not keep it (withheld, no credit line, a licence family the allowlist `media.collections`
  *   lacks, no image); `None`: it would be considered for a place — the server's match rules (names on the map, dates)
  *   decide the rest
  */
final case class MediaVerdict(
    key: MediaRecord.Key,
    item: Option[MediaItem],
    licence: Option[domain.Licence.Id],
    refused: Option[Problem]
)
