package com.histmersion.util.data

/** Today's owners and their tables, in one table until each owner registers its own (the catalogue, the media, the
  * library and research register their own: `domain.catalogue.CatalogueData`, `domain.media.MediaData`,
  * `domain.library.LibraryData`, `domain.research.ResearchData`).
  */
object TablesAdapter:
  val AutomationOwner: DataOwner = DataOwner("automation")
  val CatalogueOwner: DataOwner = DataOwner("catalogue")
  val MediaOwner: DataOwner = DataOwner("media")
  val LibraryOwner: DataOwner = DataOwner("library")
  val ResearchOwner: DataOwner = DataOwner("research")
  val PersonalOwner: DataOwner = DataOwner("personal")
  val DataArea: DataOwner = DataOwner("util.data")
  val JobsOwner: DataOwner = DataOwner("util.jobs")
  val IdentityOwner: DataOwner = DataOwner("util.identity")
  val DatabaseOwner: DataOwner = DataOwner("util.database")

  /** The order every operation runs the owners in: the domains bottom up, personal, then the supporting areas. */
  val owners: List[DataOwner] = List(
    AutomationOwner,
    CatalogueOwner,
    MediaOwner,
    LibraryOwner,
    ResearchOwner,
    PersonalOwner,
    DataArea,
    JobsOwner,
    IdentityOwner,
    DatabaseOwner
  )

  def of(owner: DataOwner): List[OwnedTable] = owner match
    case AutomationOwner =>
      OwnedTable.all(
        "agent_allowances",
        "agent_autopilot",
        "agent_leases",
        "agent_managers",
        "agent_messages",
        "agent_role_chains",
        "agent_role_fallthrough",
        "agent_state",
        "agent_steps",
        "approvals",
        "manager_events",
        "manager_hint_uses",
        "manager_hints",
        "manager_items",
        "plan_pools",
        "plan_quota_readings",
        "plan_quota_refreshes",
        "goals",
        "goal_aliases",
        "goal_focus",
        "goal_links",
        "goal_schedules",
        "queue_items"
      )
    case PersonalOwner => OwnedTable.all("bookmarks", "notes", "read_marks", "ui_seen")
    case DataArea      => OwnedTable.all("merges", "merge_decisions", "merge_ledger", "merge_sessions")
    case JobsOwner     => OwnedTable.all("jobs")
    case IdentityOwner => OwnedTable.all("instance", "uid_aliases", "tombstones", "alias_tombstones")
    case DatabaseOwner => OwnedTable.all("flyway_schema_history", "spatial_ref_sys")
    case _             => Nil
