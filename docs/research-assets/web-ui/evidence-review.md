# What the evidence says helps people work with complex, information-dense applications

*Literature review for the Histmersion web UI redesign — 2026-10-27*

**What this covers.** Empirical HCI, cognitive psychology, information visualisation and cartography, plus the
relevant ISO 9241 parts and WCAG 2.2. It reports what each study showed, with sample sizes and effect sizes
where the paper gives them, and how strong the evidence is. Each topic ends with a concrete **Implication for
Histmersion**: a self-hosted map × timeline browser of scholarly sources, with compiled articles,
place/period/topic filters, and supervision of background AI agents and jobs, meant to be usable by
non-technical people without instructions.

**Evidence grades used throughout**

| grade | meaning |
|---|---|
| **A** | meta-analysis, systematic review, or several independent controlled studies that agree |
| **B** | one or a few controlled experiments, or a large logged field study |
| **C** | standard or expert consensus (ISO, W3C), or a theory-driven model with partial validation |
| **D** | practitioner or vendor data, a method that isn't peer-reviewed, or opinion. Use as a hint, not a rule |

**Verification.** DOIs were checked against Crossref on 2026-10-27. Quoted numbers come from the paper's abstract
or full text where I could read it. Figures I could only get from a secondary source are marked as such.

---

## 1. Information density and cognitive load

### Findings

- **Working-memory limits.** Miller (1956) reported a span of about 7±2 items for absolute judgement and
  immediate memory. Cowan (2001) re-analysed the literature and put the capacity of the focus of attention at
  **about 4 chunks** when rehearsal and chunking are prevented. The common "max 7 menu items" rule misapplies
  Miller: items you can see on screen don't have to be held in memory (see recognition over recall, §3). What
  does follow is narrower. **Anything a user must carry from one view to another** should stay at about 3–4
  chunks: filter state, a value to compare, where they came from. *Grade A for the capacity limit; D for the
  menu rule.*
- **Visual search and set size.** In feature-integration theory (Treisman & Gelade 1980), a target that differs by
  one salient feature "pops out": search time barely changes as items are added. A target defined by a
  combination of features needs serial search, where time grows linearly with the number of items. Wolfe (1998)
  analysed about 1 million trials and found search efficiency to be a continuum, not two classes. Wolfe &
  Horowitz (2017) name five factors that guide attention: bottom-up salience, top-down feature guidance, scene
  structure, prior history, and value. **Density costs time only when the target cannot be told apart by a
  pre-attentive feature** such as colour, size, motion or position. *Grade A.*
- **Clutter measures.** Rosenholtz et al. (2005, CHI) and Rosenholtz, Li & Nakano (2007, *J. Vision*) built
  *feature congestion* and *subband entropy* clutter measures. Both predicted search performance on maps and
  displays, and Henderson, Chanceaux & Smith (2009) found clutter slows search in real scenes. The point for
  design: clutter is not item count. It is **how much room is left for a new item to be salient.** A dense display
  with a restrained palette can be less cluttered than a sparse one full of colour. *Grade B.*
- **Density on screen.** Tullis (1983, *Human Factors*) reviewed alphanumeric display formatting. **Grouping and
  local density** predicted search time better than overall density alone, and well-grouped dense screens beat
  poorly grouped sparse ones. Michalski, Grobelny & Karwowski (2006) found similar layout effects on GUI task
  efficiency. *Grade B (older, text-mode work, but the grouping result has held).*
- **Experts vs novices: the expertise-reversal effect.** Cognitive-load theory (Sweller 1988; Chandler & Sweller
  1991) finds that split-attention formats and redundant material raise load and hurt novices. Kalyuga et al.
  (1998; 2003) showed that **guidance and integration which help novices hurt experts**: experts must reconcile
  the redundant material with what they already know. Hollender et al. (2010) map cognitive-load theory onto HCI.
  This is the strongest evidence that **one density cannot fit both audiences.** *Grade A in instructional
  settings; transfer to UI is plausible but rests on fewer direct studies (Grade B–C).*
- **Tufte's minimalism, tested.** Tufte's "maximise data-ink" is design doctrine, not an experimental result.
  Inbar, Tractinsky & Meyer (2007) found that 87 students preferred the *non*-minimal bar chart. Bateman et al.
  (2010, CHI) found that embellished charts were **interpreted no less accurately** and were remembered better
  after 2–3 weeks. Borkin et al. (2013) found related memorability effects. Decorative minimalism is not
  supported as a usability rule, and neither is decoration. **Non-data elements should do a job** (labels,
  grouping, landmarks) and should not compete for salience. *Grade B; contested.*

### Implication for Histmersion

Keep the map, timeline and lists dense, which is what experts want, but make **the one thing the user is looking
for pre-attentively distinct**: the selected source, the active filter's matches, a failed job. Group dense
regions visibly. Never make users hold more than about 4 items in their head across views. Show the active
filter state as persistent visible chips. Treat colour as a scarce channel and spend it on the current
task-relevant distinction.

---

## 2. Progressive disclosure, adaptive and adaptable interfaces, layered interfaces

### Findings

- **Training wheels.** Carroll & Carrithers (1984, *CACM*) blocked error-prone advanced functions in a word
  processor for new users. They got started faster, made fewer errors and spent less time recovering, with no
  later penalty. Catrambone & Carroll (1987, CHI+GI) found that learning transferred to the full system. *Grade B.*
- **Multi-layer interfaces.** Shneiderman (2003, CUU) proposes interfaces with layers of increasing capability
  that users move between deliberately. The paper argues the case with a few small studies; it is not strong
  evidence of effect size. *Grade C.*
- **Personalised vs full interfaces.** McGrenere, Baecker & Booth (2002, CHI) gave 20 users a personal layer of
  Word 2000 that contained only the functions they chose. Most preferred it, and it was easier to navigate and
  learn. **The cost of hiding.** Findlater & McGrenere (2007; 2010, *IJHCS*) showed that reduced-function and
  personalised interfaces lower **feature awareness**: users recognise fewer of the features they haven't used.
  So hiding speeds up the current task and slows the discovery of new ones. *Grade B.*
- **Adaptive vs adaptable menus.** Findlater & McGrenere (2004, CHI) compared static, adaptive (the system
  reorders items) and adaptable (the user reorders) menus. **Static menus were faster than adaptive ones**, and
  most participants preferred the adaptable menus. Gajos et al. (2008, CHI) found that **accuracy and
  predictability of adaptation both raise satisfaction, and accuracy matters more for performance.** Gajos et al.
  (2006, AVI) found that **split** designs, which *copy* predicted items to a separate area while leaving the
  originals in place, beat designs that *move* items. Findlater & McGrenere (2008) found adaptation helps more on
  small screens. *Grade B; the results agree across studies.*
- **Split menus.** Sears & Shneiderman (1994, *TOCHI*) put the most-used items at the top of a menu, above the
  stable full list. Selection times fell by **17–58%** across field sites, and a controlled experiment confirmed
  the speed gain and higher preference. *Grade B.*
- **Ephemeral adaptation.** Findlater, Moffatt, McGrenere & Dawson (2009, CHI) showed predicted items at once
  and faded the rest in over about 500 ms, so nothing moves. This was faster than static menus when the
  predictions were accurate. It is one of the few adaptive techniques without a spatial-stability cost. *Grade B.*
- **Visible vs hidden navigation.** Pernice & Budiu (2016, NN/g) ran 179 participants on 6 sites. With hidden
  navigation on desktop, people used the navigation in 27% of cases, against 48–50% when it was visible or
  combined. Tasks took **≥39% longer**, content discoverability fell by **more than 20%**, and difficulty ratings
  rose (2.6 vs 2.1 on a 7-point scale). *Grade B–D: a large sample with a stated method, but not peer-reviewed.*
- **Moving from novice to expert.** Cockburn, Gutwin, Scarr & Malacria (2014, *ACM Computing Surveys*) and Scarr
  et al. (2011, CHI) found that users stay on slow novice methods because switching to an expert method brings a
  temporary **performance dip**. Lane et al. (2005) found even frequent users rarely switch to shortcuts. Expert
  paths should therefore be **visible inside the novice path**, for example a shortcut shown beside its menu
  item. *Grade A–B.*

### Implication for Histmersion

Use a **stable, static primary layout** and no automatic rearranging. For frequency, use **split or
recently-used shelves that copy items** ("Recent places", "Recent sources") above stable lists. Use progressive
disclosure only for genuinely advanced controls such as SQL, raw edges, agent model settings and bbox entry, and
always leave **a visible, labelled entry point** ("Advanced filters…"), never a hidden one. An optional, explicit
"simple / full" layer switch (adaptable, not adaptive) is supported by the evidence. Show keyboard shortcuts next
to buttons and menu items.

---

## 3. Guiding users without getting in the way

### Findings

- **Defaults.** Jachimowicz et al. (2019, *Behavioural Public Policy*) meta-analysed 58 studies (n ≈ 74,000)
  and found an average default effect of **d ≈ 0.68**, varying with context. Johnson & Goldstein (2003,
  *Science*) found organ-donor consent of 86–100% in opt-out countries against 4–28% in opt-in countries. In
  software, most people keep the defaults (§11). **The default is the design for most users.** *Grade A.*
- **Signifiers and "flat" design.** Norman (1999, *interactions*) separates affordances from perceived
  affordances and conventions; the 2013 revision of *Design of Everyday Things* calls the latter "signifiers".
  Moran (2017, NN/g) ran an eye-tracking study with 71 participants on 9 page pairs that differed only in
  signifier strength. **Weak flat signifiers took 22% longer to find and drew 25% more fixations** (p < .05).
  Burmistrov et al. (2015, INTERACT) found a search-time cost for flat icons and buttons. *Grade B.*
- **Recognition over recall.** Recognition memory is far more reliable than free recall (Mandler 1980,
  *Psychological Review*). It is heuristic 6 in Nielsen's list and part of ISO 9241-110 "self-descriptiveness".
  Users should pick filter values from what exists, not type them from memory. *Grade A for the memory result;
  C for the UI heuristic.*
- **Feedforward.** Vermeulen et al. (2013, CHI) define feedforward as telling users *what will happen before they
  act*. OctoPocus (Bau & Mackay 2008, UIST) showed that on-the-fly feedforward helps people learn gestures. Useful
  forms here: previews, "this will start 12 jobs", and counts on filter options. *Grade B–C.*
- **Onboarding: tours vs contextual help vs doing.**
  - *Minimalist instruction:* Carroll et al. (1987, *HCI*) found that a task-oriented "minimal manual" let people
    learn faster and perform better than a conventional manual. Learning by doing on real tasks, with error
    recovery built in, is the best-supported approach. *Grade B.*
  - *Animated demonstrations:* Palmiter & Elkerton (1993, *HCI*) found that demos gave faster immediate
    performance but **worse retention and transfer** after a delay than text. People imitate a demo without
    encoding it. *Grade B.*
  - *Contextual help:* ToolClips (Grossman & Fitzmaurice 2010, CHI), short video clips inside tooltips, raised
    understanding and task completion compared with plain tooltips. Stencils tutorials (Kelleher & Pausch 2005)
    cut tutorial errors but did not improve learning. *Grade B.*
  - *Tutorials vs skipping:* Kendrick (2020, NN/g) tested 70 participants on 4 iOS apps. Task success was 91% with
    the tutorial and 94% after skipping it (n.s.). **Tutorials made tasks feel harder**: Single Ease Question
    (SEQ) ratings were 4.92 vs 5.49, p = .047. *Grade B–D.*
  - *Tour completion:* vendor data from Chameleon (2019–2024 benchmark reports) shows completion falls with
    length. Three-step tours are completed about 66–72% of the time, five-step about 22% and seven-step about
    16%. The average is reported variously at 33–61% because methods differ between years. *Grade D: vendor
    product data, not peer-reviewed. Use only as a direction.*
  - Grossman, Fitzmaurice & Attar (2009, CHI) survey software learnability and separate initial learnability from
    extended learnability. A first-run tour addresses only the first. *Grade C.*
- **Empty states.** I found no controlled study of empty-state design. The guidance is practitioner lore. It is
  consistent with feedforward and minimalism (say what goes here and give one action that fills it), but it is
  untested. *Grade D.*

### Implication for Histmersion

The first screen should be **a useful default view**: the map and timeline with real data and sensible default
filters. It should not be a tour. Teach in context with labelled controls, tooltips that say *what happens*,
result counts on filter options, and empty states that offer one concrete next action ("No sources in this
period. Widen to 3500–1500 BCE?"). Make every control's effect visible before commitment (feedforward). If there
is a tour at all, keep it to ≤3 steps, make it optional, and let it be replayed from Help.

---

## 4. Grouping, layout, navigation and multiple views

### Findings

- **Gestalt grouping.** Proximity, similarity and common region are among the most robust findings in perception
  (Wagemans et al. 2012, *Psychological Bulletin*, centennial review). Palmer (1992, *Cognitive Psychology*)
  showed that **common region (a shared enclosure or background) can override proximity.** This is the evidence
  for panels and cards as grouping devices. *Grade A.*
- **Information scent and foraging.** In Pirolli & Card (1999, *Psychological Review*), people follow cues
  ("scent"), such as link labels and snippets, according to their expected value against the cost of following
  them. Chi et al. (2001) and Card et al. (2001, both CHI) showed that scent predicts navigation paths and that
  **weak scent leads to abandonment.** Larson & Czerwinski (1998, CHI) and Kiger (1984) found that **broad, shallow
  hierarchies with clear category labels** beat deep ones. *Grade A–B.*
- **Task-oriented vs object-oriented navigation.** I found no controlled study that compares the two directly for
  web apps. ISO 9241-110 (2020) gives "suitability for the user's tasks" as principle 1, and Grudin (1989) argues
  for consistency with the task over abstract consistency. Practice favours objects (sources, articles, places)
  as the primary nouns and tasks as entry points into them. *Grade C–D.*
- **Overview+detail, zooming, focus+context.** Cockburn, Karlson & Bederson (2008, *ACM Computing Surveys*) is the
  definitive review. Overview+detail is broadly preferred and helps orientation, but costs screen space and
  attention switches. Focus+context distortion can impair target acquisition and judgement. Zooming alone causes
  disorientation. **Results depend on the task.** Hornbæk, Bederson & Plaisant (2002, *TOCHI*) tested 32 users on
  2 maps: 80% preferred the overview, but correctness did not differ, and **on one map users were faster
  without the overview** because semantic zooming supplied the cues. *Grade A (review).*
- **Multiple windows vs zooming for comparison.** Plumlee & Ware (2006, *TOCHI*) found that when a comparison
  involves more items than visual working memory holds, **multiple simultaneous windows beat zooming**. Zooming
  produced much higher error rates. *Grade B.*
- **Coordinated multiple views.** North & Shneiderman (2000, *IJHCS*) found that a coordinated overview+detail
  improved performance by **30–80%** over detail-only and uncoordinated interfaces, depending on the task. Wang
  Baldonado, Woodruff & Kuchinsky (2000, AVI) give eight guidelines: diversity, complementarity, decomposition,
  parsimony, space/time resources, self-evidence, consistency, attention management. Roberts (2007) reviews the
  field. *Grade B for linked-view benefit; C for the guidelines.*
- **The visual information-seeking mantra.** "Overview first, zoom and filter, then details on demand"
  (Shneiderman 1996) was offered as a guideline, not a tested result. Craft & Cairns (2005) reviewed work that
  cites it and found **no empirical validation**; it is used as a design rationale, not tested. Hornbæk & Hertzum
  (2011) show that "overview" itself is under-defined. *Grade C: plausible and widely used, but not evidence.*
- **Interactive latency.** Liu & Heer (2014, *TVCG*) added **500 ms** of latency to exploratory visual analysis.
  It reduced interaction, dataset coverage and the rate of insights, and **the effect carried over** to later
  sessions. Card, Robertson & Mackinlay (1991) set out the 0.1 s / 1 s / 10 s perceptual thresholds. *Grade B.*

### Implication for Histmersion

Linked map ↔ timeline ↔ list views with **brushing that responds in under 100 ms** are directly supported. Keep
the overview (the timeline histogram, a mini-map) and do not use fisheye distortion. For comparison tasks, such as
two sources, two periods or two regions, offer side-by-side panes rather than toggling or zooming. Use shallow
navigation with label text that carries scent ("Sources from Anatolia, 2000–1500 BCE (47)"). Group related
controls in visibly enclosed regions.

---

## 5. Windows, panels, tabs and pages; deep links and persistence

### Findings

- **Window management overhead.** Bly & Rosenberg (1986, CHI) found that tiled windows beat overlapping ones for
  tasks with little window manipulation, while overlapping helped experts in irregular tasks. Kandogan &
  Shneiderman (1997, CHI) found that hierarchical Elastic Windows beat independent overlapping windows on
  multi-window tasks. Hutchings et al. (2004) found that users of more screen space do fewer window operations.
  The evidence says manual window juggling is overhead, so **give users automatic, stable tiling.** *Grade B.*
- **Working spheres and interruption.** González & Mark (2004, CHI) observed information workers. They spent about
  3 minutes on an event before switching and about 11 minutes in a "working sphere", and 57% of spheres were
  interrupted. Rooms (Henderson & Card 1986, *TOG*) introduced task-based workspaces to cut contention for screen
  space. **Restoring a working context has value.** *Grade B.*
- **Tabs as working memory.** Dubroy & Balakrishnan (2010, CHI) found tabs used as short-term bookmarks,
  reminders and parallel workspaces. Huang & White (2010, Hypertext) found parallel browsing common in large logs.
  Weinreich et al. (2008, *TWeb*) found that opening links in new windows or tabs had grown and Back-button use
  had fallen. Chang et al. (2021, CHI) interviewed 10 information workers and surveyed 103 more. People keep tabs
  open to avoid losing context and to avoid the cost of re-finding, and suffer "tab overload" as a result.
  **Users expect to open things in new tabs, and they rely on it.** *Grade B.*

### Implication for Histmersion

Every source, article, place, period, filtered view and job must be **a real URL** (a deep link), so that middle
click, Cmd-click, Back and bookmarks work. The full view state (filters, map extent, time window, selection)
belongs in the URL. Within one tab, use a **fixed tiled layout** of map, timeline, list and detail with resizable
splits, rather than floating windows. Persist the last workspace per user and restore it on return.

---

## 6. Consistency, naming and typography

### Findings

- **Consistency helps transfer.** Polson, Muncher & Engelbeck (1986, CHI) found that learning time for a new
  procedure fell in proportion to the procedural elements it shared with known ones (common-elements transfer).
  Ozok & Salvendy (2000, *Ergonomics*) found that inconsistency (physical, communicational, conceptual) increased
  errors and lowered satisfaction. Satzinger & Olfman (1998) found that consistency across applications improved
  mental models. *Grade B.*
- **…but consistency is not the goal itself.** Grudin (1989, *CACM*) argues that consistency with **the user's task
  and expectations** should beat internal uniformity when the two conflict. For example, the most frequent
  choice belongs at the top of a menu even if other menus are alphabetical. ISO 9241-110 (2020) frames this as
  "conformity with user expectations". *Grade C (argument with examples).*
- **Plain language over system identifiers.** Shulman et al. (2020, *J. Lang. Soc. Psych.*) found that jargon
  **reduces processing fluency and engagement even when the term is defined.** Morkes & Nielsen (1997, NN/g; 51
  users) found that concise, scannable, objective text improved measured usability by 124% combined. Treat that
  as practitioner work with a stated method. *Grade B for jargon; D for the 124%.*
- **Headings and signalling.** Lorch (1989, *Educational Psychology Review*) found that headings, previews and
  typographic signals improve memory for the signalled content and help readers find their way. *Grade A–B.*
- **Font size and line length.** Rello, Pielot & Marcos (2016, CHI; n = 104, eye-tracking) found that **larger
  text (18 pt and above) improved readability and comprehension** compared with 10–14 pt; line spacing had no
  effect. Dyson & Haselgrove (2001, *IJHCS*) and Dyson's review (2004, *BIT*) found that longer lines (about 100
  characters) are *read faster*, while about 55 characters gave the best comprehension and medium lines are
  preferred. The popular "45–75 characters" rule rests on preference and comprehension, not speed. *Grade B;
  partly contested.*
- **ISO 9241-112 (2017)** lists detectability, freedom from distraction, discriminability, interpretability,
  conciseness, and internal/external consistency as presentation principles. **ISO 9241-125 (2017)** gives
  detailed visual-presentation guidance on text, colour and coding. *Grade C.*

### Implication for Histmersion

Use **one vocabulary** for the same concept everywhere: "source", never "record", "row" or "citekey". Show human
labels such as titles, place names and period names, with system identifiers (citekeys, job IDs) shown second or
on request. Map statuses like `superseded` and `unassessed` to plain explanations with a tooltip. Article text
should be ≥18 px with a measure of about 60–75 characters and a clear heading hierarchy. Tables can be denser
but need consistent alignment and units.

---

## 7. Icons vs labels

### Findings

- Wiedenbeck (1999, *BIT*) compared label-only, icon-only and icon+label versions of an end-user application.
  **Label-only and icon+label groups did better than icon-only in initial learning**, and icon+label was rated
  easiest. Icon-only users narrowed the gap with practice. *Grade B.*
- McDougall, Curry & de Bruijn (1999) published norms for 239 symbols on concreteness, complexity, familiarity,
  meaningfulness and semantic distance. McDougall et al. (2000, *JEP: Applied*) found that concreteness helps
  early and complexity slows. Isherwood, McDougall & Curry (2007, *Human Factors*) found that **familiarity and
  concreteness matter for novices, while semantic distance (how directly the icon depicts its function) keeps
  mattering** for experienced users. Blankenberger & Hahn (1991) report related effects. *Grade A–B.*
- **Comprehension standards.** ISO 9186-1:2014 specifies an open-ended comprehension test: participants say what
  a symbol means in context, and judges score the answers into correct, partly correct, wrong, opposite and
  no-answer categories. The commonly cited acceptance levels are **≈67% correct (ISO public-information
  symbols)** and **85% correct with ≤5% critical confusions (ANSI Z535.3 safety symbols)**. I could confirm these
  only from secondary sources and could not read the standards' text (paywalled). *Grade C.*
- NN/g guidance that icons need text labels except for a handful of universal ones (search, home, print) is
  consistent with the controlled studies above. *Grade C–D.*

### Implication for Histmersion

**Label every icon** in toolbars and navigation. Icon-only is acceptable only for the universal few, and still
needs a tooltip and an `aria-label`. Domain icons, such as for a myth, culture, finding, contested status or agent
run, should be tested before release with a cheap ISO 9186-style open-ended test (see §13). Keep only icons that
reach ≥67% correct.

---

## 8. Map-specific UX

### Findings

- **Interactive cartography reviews.** Roth (2013, *JOSIS*) reviews what is known about interactive maps. User
  studies are sparse, and many practices rest on cartographic convention. *Grade C (review).*
- **Point clutter.** Korpi & Ahonen-Rainio (2013, *Cartographic Journal*) classify ways to reduce clutter among
  point symbols in map mashups: aggregation, selective display, displacement, and so on. **I found no robust
  controlled study of marker clustering or spiderfying.** Meier (2016, *IJAEIS*) evaluated marker clusters
  against heatmaps and argues that clusters are often misread as quantities. Fürhoff (2019, PeerJ preprint)
  criticises overuse of clustering. *Grade C–D.* By perception theory, the count shown on a cluster badge is
  read as text, so it must be legible and its meaning (sources? findings?) explicit.
- **Legends.** Gołębiowska (2013/2015, *Cartographic Journal*) combined usability metrics with think-aloud and
  found that legend layout and its grouping logic affect how efficiently people read a thematic map. *Grade B
  (small study).*
- **Animation vs small multiples.**
  - Robertson et al. (2008, *TVCG*): for **analysis, animation was the least effective form**. Both static trend
    displays were significantly faster, and small multiples were more accurate. In presentation, animation was
    fastest and enjoyed but produced many errors. *Grade B.*
  - Archambault, Purchase & Pinaud (2011, *TVCG*): small multiples were generally faster than animation for
    dynamic graphs. *Grade B.*
  - **Counter-evidence for maps:** Griffin et al. (2006, *Annals AAG*) found that map readers identified
    **space-time clusters faster and more accurately with animated maps** than with static small multiples, with
    effects depending on pace. *Grade B.*
  - Tversky, Morrison & Bétrancourt (2002, *IJHCS*) found that many apparent benefits of animation disappear when
    the static version carries the same information. Animation helps mainly for **showing change in itself**,
    and when it is slow, controllable and simple. *Grade A (review).*
  - Heer & Robertson (2007, *TVCG*) found that **staged animated transitions** between views improved object
    tracking and value estimation compared with abrupt changes. *Grade B.*
  - Harrower (2007, *Cartographica*) found that animated maps exceed attention and memory limits, and recommends
    user-controlled pace, play/pause and a step control. *Grade C.*
- **Change blindness.** Rensink, O'Regan & Clark (1997, *Psychological Science*) and Simons & Rensink (2005)
  showed that people miss large changes made during a disruption. Nowell, Hetzler & Tanasse (2001, InfoVis) found
  this in a visualisation. **Updates that happen during a re-render or out of view go unnoticed** unless they are
  highlighted. *Grade A.*
- **Reduced motion.** WCAG 2.2 SC 2.3.3 (AAA) and SC 2.2.2 (A, pause/stop/hide) apply, as does the CSS
  `prefers-reduced-motion` media feature. *Grade C.*

### Implication for Histmersion

For "how did the distribution change over time", give **two modes**. A *scrubbable* time slider with play, pause
and step, and a speed control; the map evidence favours animation for spotting clusters. And a *small-multiples*
view of N periods side by side for accurate comparison. Never auto-play. Use short, staged transitions (about
200–400 ms) when filters change. When data updates, highlight what changed, for example "+12 sources in view",
because of change blindness. Respect `prefers-reduced-motion`. Use clustering with explicit, legible counts and an
honest legend. Spread coincident points out into a list on click (spiderfy) as a practical convention, not an
evidence-backed technique. Keep the legend visible and grouped by meaning, not in a collapsed corner.

---

## 9. Supervising automation and background agents

### Findings

- **Situation awareness.** Endsley (1995, *Human Factors*) defines three levels: perception, comprehension and
  projection. Displays should support all three, not just raw status. *Grade C (theory, widely validated).*
- **Levels of automation and out-of-the-loop costs.** Parasuraman, Sheridan & Wickens (2000) classify automation
  by stage (information acquisition, analysis, decision, action) and level. Endsley & Kiris (1995) found that
  **intermediate levels of automation** kept operators more aware than full automation. Onnasch et al. (2014,
  *Human Factors*), a meta-analysis, found a "lumberjack effect": **more automation improves routine performance
  and lowers workload, but when the automation fails, performance and situation awareness are worse.**
  Bainbridge (1983, "Ironies of automation") makes the classic argument that automation leaves humans with the
  hardest monitoring tasks. *Grade A.*
- **Trust, misuse and automation bias.** Parasuraman & Riley (1997) set out use, misuse, disuse and abuse. Lee &
  See (2004) argue for **calibrated trust**. Goddard, Roudsari & Wyatt (2012, *JAMIA*) systematically reviewed
  automation bias, where people over-accept automated advice. Bansal et al. (2021, CHI) found that AI explanations
  **increased acceptance of AI recommendations whether or not they were correct**. Buçinca, Malaya & Gajos (2021,
  CSCW) found that *cognitive forcing functions*, such as asking for the user's judgement first, reduce
  over-reliance but are liked less. **Approval queues that ask for one-click "Approve" invite rubber-stamping.**
  *Grade A–B.*
- **Human-AI guidelines.** Amershi et al. (2019, CHI) propose 18 guidelines validated with 49 practitioners on
  20 products. Among them: make clear what the system can do and how well; support efficient dismissal and
  correction; scope services when in doubt; explain why; remember recent interactions; notify about changes.
  Horvitz (1999) sets out mixed-initiative principles. *Grade C.*
- **Interruption costs.** Iqbal & Horvitz (2007, CHI) logged 27 users for two weeks. Alert-driven switches took
  about **10 minutes**, plus another **10–15 minutes** before returning to focused work, and **27% of
  suspensions lasted more than 2 hours** before resumption. Visible suspended windows helped resumption. Mark,
  Gudith & Klocke (2008, CHI) found that interrupted people work faster but with **more stress, frustration and
  workload (NASA-TLX)**. Bailey & Konstan (2006) found that interruptions raise task time, errors and annoyance,
  more so mid-task than at boundaries. Iqbal & Bailey (2008, CHI) found that **deferring notifications to
  breakpoints** reduced frustration and reaction time. McFarlane (2002) found that *negotiated* interruption
  (the user chooses when) was best overall. Fitz et al. (2019, *CHB*), in a field experiment with N ≈ 237, found
  that **batching notifications** three times a day improved well-being, attention and productivity compared with
  delivering them as they arrived. Altmann & Trafton (2002) model why resumption cues help. *Grade A.*
- **Status visibility.** Myers (1985) found that 86% of users preferred percent-done indicators. Harrison et al.
  (2007, UIST) found that progress-bar behaviour changes perceived duration. ISO 9241-110 lists
  "self-descriptiveness" and "controllability". *Grade B–C.*
- **Unified activity feeds.** I found **no controlled study** that compares a unified activity timeline with
  separate run, job and log screens. The case for unifying them rests on situation-awareness theory: one place to
  perceive, comprehend and project. *Grade C–D.*

### Implication for Histmersion

- One **Activity** view: a time-ordered feed where runs, jobs, queue tasks and log entries are typed items with
  a shared status vocabulary (queued, running, needs you, done, failed). Filter by type, without separate apps.
- An "at a glance" strip that answers Endsley's three levels: what is running now; is anything wrong or waiting
  for me; what will happen next (the scheduled runs).
- **Pull, not push.** A quiet badge count. Non-urgent results are batched. Interrupt only for failures that need a
  decision, and even then deliver at breakpoints, for example on navigation rather than mid-reading.
- **Approval queues that resist rubber-stamping.** Show the evidence and the diff before the Approve button. For
  consequential approvals, ask for a reason or a quick user judgement, the forcing function Buçinca found
  effective. Offer bulk approve only for low-risk classes.
- **Resumption cues.** "Since you were last here: 3 runs finished, 1 needs review."
- Every agent action is attributable and reversible, and visible as an item in the feed.

---

## 10. Accessibility that improves usability for everyone

### Findings

- **Benefit to non-disabled users.** Schmutz, Sonderegger & Sauer (2016, *Human Factors*) had 61 non-disabled
  participants use websites at high, low and very low WCAG 2.0 conformance. **Higher conformance gave better
  efficiency, effectiveness and satisfaction.** Gernsbacher (2015) reviews evidence that captions benefit
  everyone's comprehension and memory. Fitts' law (Fitts 1954) shows that **larger targets are faster to hit for
  everyone.** *Grade A–B.*
- **WCAG 2.2 AA criteria most relevant here** (W3C Recommendation, 5 Oct 2023, updated 12 Dec 2024):
  - **1.4.3 Contrast (Minimum)**: text 4.5:1, large text 3:1. **1.4.11 Non-text Contrast**: UI parts and
    meaningful graphics such as map symbols, focus rings and slider handles 3:1.
  - **1.4.1 Use of Color** (A): status must not be conveyed by colour alone. This matters for statuses and the
    map legend.
  - **1.4.10 Reflow**: usable at 320 CSS px wide with no two-dimensional scrolling. Maps and data tables are
    exempt only where 2-D layout is essential.
  - **1.4.12 Text Spacing**, **1.4.13 Content on Hover or Focus**: tooltips must be dismissible, hoverable and
    persistent.
  - **2.1.1 Keyboard** (A): the map, timeline and slider must be operable by keyboard.
  - **2.4.7 Focus Visible**, **2.4.11 Focus Not Obscured (Minimum)** (new in 2.2): sticky headers and panels must
    not hide the focused element.
  - **2.5.7 Dragging Movements** (new, AA): **any drag action, such as map pan or a time-range brush, needs a
    single-pointer alternative**, for example buttons or input fields.
  - **2.5.8 Target Size (Minimum)** (new, AA): **24×24 CSS px**, or enough spacing around smaller targets.
  - **2.2.2 Pause, Stop, Hide** (A): auto-updating content such as live job feeds needs a pause.
  - **3.2.6 Consistent Help** (A), **3.3.7 Redundant Entry** (A), **3.3.8 Accessible Authentication** (AA).
  - **2.3.3 Animation from Interactions** (AAA, but cheap): honour reduced-motion.
  - *Grade C (standard); the 2.2 additions rest on W3C working-group research into cognitive accessibility.*

### Implication for Histmersion

Treat WCAG 2.2 AA as a floor. The specific risks are the map and timeline: dragging (2.5.7), keyboard control
(2.1.1), symbol contrast (1.4.11), and colour-only status (1.4.1). Another is live activity panels, which need
2.2.2 and an `aria-live` region of modest politeness. Make all targets ≥24 px, and preferably ≥32 px for primary
controls.

---

## 11. Settings, setup and defaults

### Findings

- Mackay (1991, CHI) studied 51 users of a Unix environment. **Most customised little.** Customisation was
  triggered by external events, such as a new system or a colleague's example, and blocked mainly by **lack of
  time** and by uncertainty about what settings do. People rarely revisited customisations. *Grade B.*
- Page et al. (1996, CHI) studied 101 WordPerfect users. 92% customised *something*, but mostly small, frequent
  tweaks such as button bars, and changes concentrated in a few features. *Grade B.*
- Shah & Sandvig (2008) found that most users of Wi-Fi routers left security defaults as shipped. "Software
  defaults are de facto regulation." *Grade B.*
- Jachimowicz et al. (2019) give a meta-analytic d ≈ 0.68 for default effects (§3). *Grade A.*
- **Setup wizards.** I found no strong controlled evidence that wizards beat good defaults. The consistent
  findings are that defaults stick and that most users never visit settings, which favours **shipping correct
  defaults and deferring configuration until it is needed.** *Grade C–D.*

### Implication for Histmersion

Make it work with **zero setup**. Every setting has a safe, sensible default, and most users will never change it.
Ask for configuration **just in time**, with the reason. For example, ask for an API key only when the user first
starts an agent run, and explain what it unlocks. Group the settings page by task ("Agents", "Map", "Library"),
not by subsystem. Use plain labels with a one-line description and the current value. Show what has changed from
the default, with a "reset" option.

---

## 12. Annotations, bookmarks, read marks and re-finding

### Findings

- **Annotation practice.** Marshall (1997, DL) studied used university textbooks. Annotations were mostly
  telegraphic, in-place marks (highlights, brackets, margin words) acting as **placemarks and procedural
  signposts**, not essays. Marshall (1998) distinguishes formal from informal, explicit from implicit, and
  permanent from transient annotations. Marshall & Brush (2004, JCDL) found that **only a small fraction of
  personal annotations were ever shared**, and those were rewritten for others. *Grade B.*
- **Reading on screen.** O'Hara & Sellen (1997, CHI) found that paper beat screen for annotating *while* reading,
  moving quickly through a document and spatial layout. XLibris (Schilit et al. 1998, CHI) showed that free-form
  annotation in place, with reviewable collections, closes much of that gap. *Grade B.*
- **Re-finding is the norm.** Tauscher & Greenberg (1997, *IJHCS*) found about 58% of page visits were revisits,
  Cockburn & McKenzie (2001) about 81%, and Obendorf et al. (2007, CHI) about 46%, most within an hour. Teevan et
  al. (2007, SIGIR) found that **about 40% of queries were re-finding** attempts. Teevan et al. (2004, CHI) found
  people prefer *orienteering* (stepping from a known place) to keyword search. *Grade A.*
- **Bookmarks and folders.** Abrams, Baecker & Chignell (1998, CHI) found that bookmark collections grow and
  organisation is deferred. Jones, Bruce & Dumais (2001) found people use many "keeping" methods. Whittaker et al.
  (2011, CHI) studied about 345 users and about 85,000 re-finding actions in email. **People who carefully filed
  into folders were not more successful at re-finding.** Search and threading were more efficient, and filing
  cost time. Boardman & Sasse (2004) and Bernstein et al. (2008, *TOIS*, "information scraps") found that personal
  notes are fragmented across tools and quickly lost. *Grade B.*
- **Read wear.** Hill et al. (1992, CHI) introduced "read wear" and "edit wear": marks of past use. Alexander et
  al. (2009, CHI) found that a **"footprints" scrollbar** marking previously visited positions made revisiting
  faster. *Grade B.*

### Implication for Histmersion

- **Highlights and short notes anchored in place** on article and source text, done in one gesture with optional
  text, plus a collected view of "my notes", filterable and linked back. Notes should be private by default;
  sharing is rare and needs a deliberate edit.
- **Read marks**: automatic visited state on sources and articles in every list and map popup, plus "last
  position" in long texts.
- **Bookmarks** as a single flat, searchable "Saved" list with optional tags, and no mandatory folders. Auto
  history, "Recently viewed", and search over notes and saved items will do more for re-finding than any
  organising scheme.
- Make notes and saved items **place- and time-aware** so they can appear on the map and timeline. This uses the
  orienteering behaviour people prefer.

---

## 13. Evaluation methods we can afford

### Findings

- **Heuristic evaluation.** Nielsen & Molich (1990, CHI): single evaluators find **20–51%** of problems;
  aggregating 3–5 evaluators finds far more. Jeffries et al. (1991, CHI): heuristic evaluation found the most
  problems per unit cost, but **user testing found the severe, recurring ones**. *Grade B.*
- **The evaluator effect.** Hertzum & Jacobsen (2001, *IJHCI*): evaluators analysing the same sessions overlap
  little, often under 50%. Molich's CUE studies found many problems reported by only one team. Gray & Salzman
  (1998, *HCI*) criticised the methods of many comparisons between evaluation methods. **Use several evaluators
  and fix what recurs.** *Grade A.*
- **How many users? The "5 users" debate.** Nielsen & Landauer (1993) modelled about 85% of problems found with 5
  users, assuming a mean problem-detection rate of about 0.31. Faulkner (2003): 5 users found 55–99% depending on
  the sample; **10 users found at least 80%**. Spool & Schroeder (2001): 5 users found about 35% on large websites.
  Hwang & Salvendy (2010, *CACM*), a meta-analysis: **10±2 users** for 80%. **Five per round, over several
  iterative rounds**, is defensible for formative testing of a focused task set. It is not enough for a broad,
  complex app in one round. *Grade A.*
- **Think-aloud.** Boren & Ramey (2000) contrast the classic Ericsson–Simon protocol with practice. Hertzum,
  Hansen & Andersen (2009) found that *relaxed* think-aloud changes behaviour and raises workload more than
  classic think-aloud. Alhadreti & Mayhew (2018, CHI) compare concurrent and retrospective variants. Use
  **concurrent, classic (minimal-prompt) think-aloud** for problem discovery, and don't mix timing measures with
  think-aloud sessions. *Grade B.*
- **Cognitive walkthrough.** Wharton et al. (1994). Spencer's (2000, CHI) *streamlined* walkthrough asks two
  questions per step: will the user know what to do, and will they know they did it right. Mahatody, Sagar &
  Kolski (2010) review the variants. **Well suited to "usable without instructions"**, and free. *Grade C.*
- **SUS.** Ten items, scored 0–100. Bangor, Kortum & Miller (2008, *IJHCI*) analysed about 2,300 surveys and
  found a mean of about 70. Bangor et al. (2009, *JUS*) added adjective anchors (about 68 is the usual
  "average"). Lewis (2018, *IJHCI*) is the authoritative review. Tullis & Stetson (2004, UPA) found SUS reached
  stable conclusions with about 12 participants. That paper is not peer-reviewed in a journal. **UMUX-LITE**
  (Lewis, Utesch & Maher 2013) has 2 items and correlates highly with SUS. *Grade A.*
- **NASA-TLX.** Hart & Staveland (1988); Hart (2006, "20 years later"): six workload subscales. Raw TLX, without
  pairwise weighting, is common and adequate. Use it for comparing designs of agent supervision and dense views.
  *Grade A.*
- **First-click testing.** Bailey & Wolfson (2009) reported 87% task success after a correct first click against
  46% after a wrong one. MeasuringU notes this was **not measured with click tests**. Their replication (750
  participants, 21 tasks) found a smaller effect. *Grade D, but the method is cheap and sensible.*
- **Five-second tests.** Lindgaard et al. (2006, *BIT*) found that judgements of visual appeal formed within 50
  ms are stable. That supports the claim that first impressions form fast, **not** that a five-second test
  measures usability. Use it only for "what is this page for?" comprehension. *Grade B for the basis; D as a
  usability method.*
- **Aesthetics and perceived usability.** Tractinsky et al. (2000) found that beauty is perceived as usable.
  Tuch et al. (2012, *CHB*) found the *reverse* direction after use: poor usability lowered perceived beauty.
  Hassenzahl & Monk (2010) found the beauty-to-usability inference to be weak and indirect. **Polish can inflate
  self-reported usability in a quick test**, so measure task success, not only ratings. *Grade B; contested.*
- **InfoVis-specific evaluation.** Lam et al. (2012, *TVCG*) list seven evaluation scenarios; insight-based and
  task-based evaluation fit Histmersion's map × timeline. *Grade C.*

### Cheap evaluation plan the evidence supports

1. A streamlined cognitive walkthrough of the 5–8 core tasks before each redesign round.
2. Heuristic evaluation (ISO 9241-110 principles plus Nielsen's 10) by 3 evaluators working independently,
   then merged.
3. Rounds of 5 non-technical users with classic think-aloud. Iterate ≥2–3 rounds, so 10–15 users in total.
4. Task success rate, time on task, and SUS or UMUX-LITE after each round. Add raw NASA-TLX for the
   agent-supervision tasks.
5. An ISO 9186-style open-ended comprehension test for every domain icon (§7), run unmoderated online with about
   20–50 people per icon, keeping icons that reach ≥67% correct.
6. A first-click test on navigation labels and the scent of filter labels.

---

## Ranked: the 15 design principles best supported by evidence

Ranking weighs evidence strength (number and quality of studies, effect size) and relevance to Histmersion.

1. **Keep interaction latency under about 100 ms for brushing and filtering; delays measurably reduce
   exploration.** Liu & Heer 2014; Card, Robertson & Mackinlay 1991.
2. **Respect working-memory limits: keep the state users need on screen (filters, comparison targets,
   position) instead of making them remember it.** Cowan 2001; Miller 1956; Mandler 1980; Plumlee & Ware 2006.
3. **Interrupt sparingly: batch non-urgent notifications, deliver at breakpoints, and let users choose when.**
   Iqbal & Horvitz 2007; Mark et al. 2008; Bailey & Konstan 2006; Iqbal & Bailey 2008; McFarlane 2002;
   Fitz et al. 2019.
4. **Ship good defaults; most users never change them.** Jachimowicz et al. 2019; Johnson & Goldstein 2003;
   Mackay 1991; Page et al. 1996; Shah & Sandvig 2008.
5. **Keep layouts spatially stable; don't let the system rearrange controls. Use split or copied "recent" areas
   and user-controlled (adaptable) layering instead.** Findlater & McGrenere 2004; Gajos et al. 2006, 2008;
   Sears & Shneiderman 1994; Findlater et al. 2009.
6. **Make the target task-relevant item pre-attentively distinct; group dense content visibly instead of thinning
   it.** Treisman & Gelade 1980; Wolfe 1998; Wolfe & Horowitz 2017; Rosenholtz et al. 2005, 2007; Tullis 1983;
   Palmer 1992; Wagemans et al. 2012.
7. **Link coordinated views (map ↔ timeline ↔ list) with an overview, and support side-by-side comparison
   instead of toggling.** North & Shneiderman 2000; Cockburn, Karlson & Bederson 2008; Plumlee & Ware 2006;
   Wang Baldonado et al. 2000.
8. **Accessibility (WCAG 2.2 AA) improves usability for everyone: contrast, target size, keyboard, alternatives
   to dragging, focus visibility.** Schmutz et al. 2016; Fitts 1954; Gernsbacher 2015; W3C WCAG 2.2.
9. **Design for re-finding: history, visited marks, saved items and search over them, not mandatory folders.**
   Tauscher & Greenberg 1997; Teevan et al. 2007; Obendorf et al. 2007; Whittaker et al. 2011; Alexander et al.
   2009.
10. **Supervising automation: keep humans in the loop at intermediate levels, expose state for perception,
    comprehension and projection, and add friction against over-reliance for consequential approvals.**
    Endsley 1995; Endsley & Kiris 1995; Onnasch et al. 2014; Parasuraman et al. 2000; Goddard et al. 2012;
    Bansal et al. 2021; Buçinca et al. 2021.
11. **Label icons; icon-only controls slow learning.** Wiedenbeck 1999; McDougall et al. 2000; Isherwood et al.
    2007.
12. **Strong signifiers and visible navigation: hidden or flat controls cost time and discoverability.**
    Moran 2017; Burmistrov et al. 2015; Pernice & Budiu 2016; Findlater & McGrenere 2010.
13. **Teach through use and context, not up-front tours and demos.** Carroll et al. 1987; Carroll & Carrithers
    1984; Palmiter & Elkerton 1993; Grossman & Fitzmaurice 2010; Kendrick 2020.
14. **Use plain-language, consistent labels with good information scent; prefer shallow, broad structure.**
    Pirolli & Card 1999; Chi et al. 2001; Larson & Czerwinski 1998; Kiger 1984; Shulman et al. 2020;
    Polson et al. 1986; Ozok & Salvendy 2000.
15. **Choose animation vs small multiples by task: static or small multiples for analytic comparison, controllable
    animation for spotting spatio-temporal change, staged transitions between states, and highlights for
    updates.** Robertson et al. 2008; Archambault et al. 2011; Griffin et al. 2006; Tversky et al. 2002;
    Heer & Robertson 2007; Rensink et al. 1997.

## Popular practices the evidence does NOT support

- **"No more than 7±2 items in a menu or nav."** This misreads Miller (1956). Visible items are recognised, not
  recalled. Broad, well-labelled menus beat deep ones (Kiger 1984; Larson & Czerwinski 1998).
- **System-driven adaptive UIs that reorder or hide things automatically.** They are slower than static layouts
  unless very accurate, and they reduce feature awareness (Findlater & McGrenere 2004, 2010; Gajos et al. 2008).
- **Mandatory multi-step product tours and auto-playing demo videos.** They are skipped and abandoned as they get
  longer (Chameleon vendor data, D), give no gain in task success (Kendrick 2020), and give worse retention than
  doing (Palmiter & Elkerton 1993).
- **Hamburger or hidden navigation on desktop** (Pernice & Budiu 2016).
- **Flat, signifier-less buttons and icon-only toolbars as a "clean" look** (Moran 2017; Burmistrov et al. 2015;
  Wiedenbeck 1999).
- **Tufte-style maximal data-ink minimalism as a usability rule.** It is not borne out: embellishment didn't hurt
  accuracy and helped recall (Bateman et al. 2010; Inbar et al. 2007).
- **Animation as the default way to show change over time for analysis** (Robertson et al. 2008; Archambault et
  al. 2011). The map exception is Griffin et al. 2006.
- **"Overview first, zoom and filter, details on demand" as validated law.** It is a useful heuristic with no
  direct empirical validation (Craft & Cairns 2005).
- **Elaborate folder hierarchies for bookmarks and notes.** Filing doesn't improve re-finding over search
  (Whittaker et al. 2011).
- **Gamification (streaks, badges, points) to drive engagement.** The evidence is mixed and effects are often
  novelty-driven (Hamari, Koivisto & Sarsa 2014). Contingent extrinsic rewards can undermine intrinsic
  motivation (Deci, Koestner & Ryan 1999, meta-analysis). Dark patterns measurably manipulate choices (Luguri &
  Strahilevitz 2021; Mathur et al. 2019; Gray et al. 2018), which is the opposite of calibrated user control.
- **"Five users find 85% of problems" for a complex app, in a single round** (Faulkner 2003; Spool & Schroeder
  2001; Hwang & Salvendy 2010).
- **Judging usability by attractiveness ratings or five-second tests.** Aesthetics inflates perceived usability
  (Tractinsky et al. 2000; Tuch et al. 2012).

---

## References

Abrams, D., Baecker, R., & Chignell, M. (1998). Information archiving with bookmarks: personal Web space construction and organization. *CHI '98*. https://doi.org/10.1145/274644.274651

Alexander, J., Cockburn, A., Fitchett, S., Gutwin, C., & Greenberg, S. (2009). Revisiting read wear: analysis, design, and evaluation of a footprints scrollbar. *CHI '09*. https://doi.org/10.1145/1518701.1518957

Alhadreti, O., & Mayhew, P. (2018). Rethinking thinking aloud: a comparison of three think-aloud protocols. *CHI '18*. https://doi.org/10.1145/3173574.3173618

Altmann, E. M., & Trafton, J. G. (2002). Memory for goals: an activation-based model. *Cognitive Science*, 26(1), 39–83. https://doi.org/10.1207/s15516709cog2601_2

Amershi, S., et al. (2019). Guidelines for human-AI interaction. *CHI '19*. https://doi.org/10.1145/3290605.3300233

Archambault, D., Purchase, H., & Pinaud, B. (2011). Animation, small multiples, and the effect of mental map preservation in dynamic graphs. *IEEE TVCG*, 17(4), 539–552. https://doi.org/10.1109/TVCG.2010.78

Bailey, B. P., & Konstan, J. A. (2006). On the need for attention-aware systems. *Computers in Human Behavior*, 22(4), 685–708. https://doi.org/10.1016/j.chb.2005.12.009

Bailey, R. W., & Wolfson, C. (2009). FirstClick usability testing (practitioner report); critique and replication: MeasuringU, https://measuringu.com/?p=36842

Bainbridge, L. (1983). Ironies of automation. *Automatica*, 19(6), 775–779. https://doi.org/10.1016/0005-1098(83)90046-8

Bangor, A., Kortum, P. T., & Miller, J. T. (2008). An empirical evaluation of the System Usability Scale. *IJHCI*, 24(6), 574–594. https://doi.org/10.1080/10447310802205776

Bangor, A., Kortum, P., & Miller, J. (2009). Determining what individual SUS scores mean: adding an adjective rating scale. *Journal of Usability Studies*, 4(3), 114–123. https://uxpajournal.org/determining-what-individual-sus-scores-mean-adding-an-adjective-rating-scale/

Bansal, G., et al. (2021). Does the whole exceed its parts? The effect of AI explanations on complementary team performance. *CHI '21*. https://doi.org/10.1145/3411764.3445717

Bateman, S., et al. (2010). Useful junk? The effects of visual embellishment on comprehension and memorability of charts. *CHI '10*. https://doi.org/10.1145/1753326.1753716

Bau, O., & Mackay, W. E. (2008). OctoPocus: a dynamic guide for learning gesture-based command sets. *UIST '08*. https://doi.org/10.1145/1449715.1449724

Bernstein, M., Van Kleek, M., Karger, D., & schraefel, m. c. (2008). Information scraps. *ACM TOIS*, 26(4). https://doi.org/10.1145/1402256.1402263

Blankenberger, S., & Hahn, K. (1991). Effects of icon design on human-computer interaction. *IJMMS*, 35(3), 363–377. https://doi.org/10.1016/S0020-7373(05)80133-6

Bly, S. A., & Rosenberg, J. K. (1986). A comparison of tiled and overlapping windows. *CHI '86*. https://doi.org/10.1145/22627.22356

Boardman, R., & Sasse, M. A. (2004). "Stuff goes into the computer and doesn't come out". *CHI '04*. https://doi.org/10.1145/985692.985766

Boren, T., & Ramey, J. (2000). Thinking aloud: reconciling theory and practice. *IEEE Trans. Prof. Commun.*, 43(3), 261–278. https://doi.org/10.1109/47.867942

Borkin, M. A., et al. (2013). What makes a visualization memorable? *IEEE TVCG*, 19(12). https://doi.org/10.1109/TVCG.2013.234

Buçinca, Z., Malaya, M. B., & Gajos, K. Z. (2021). To trust or to think: cognitive forcing functions can reduce overreliance on AI. *PACM HCI (CSCW)*, 5. https://doi.org/10.1145/3449287

Burmistrov, I., Zlokazova, T., Izmalkova, A., & Leonova, A. (2015). Flat design vs traditional design: comparative experimental study. *INTERACT 2015*, LNCS 9297. https://doi.org/10.1007/978-3-319-22668-2_10

Card, S. K., Robertson, G. G., & Mackinlay, J. D. (1991). The information visualizer, an information workspace. *CHI '91*. https://doi.org/10.1145/108844.108874

Card, S. K., et al. (2001). Information scent as a driver of Web behavior graphs. *CHI '01*. https://doi.org/10.1145/365024.365331

Carroll, J. M., & Carrithers, C. (1984). Training wheels in a user interface. *CACM*, 27(8), 800–806. https://doi.org/10.1145/358198.358218

Carroll, J. M., Smith-Kerker, P. L., Ford, J. R., & Mazur-Rimetz, S. A. (1987). The minimal manual. *Human-Computer Interaction*, 3(2), 123–153. https://doi.org/10.1207/s15327051hci0302_2

Catrambone, R., & Carroll, J. M. (1987). Learning a word processing system with training wheels and guided exploration. *CHI+GI '87*. https://doi.org/10.1145/30851.275625

Chameleon (2019–2024). Product Tour Benchmarks reports (vendor data). https://www.chameleon.io/benchmark-report-24

Chandler, P., & Sweller, J. (1991). Cognitive load theory and the format of instruction. *Cognition and Instruction*, 8(4), 293–332. https://doi.org/10.1207/s1532690xci0804_2

Chang, J. C., Hahn, N., Perer, A., & Kittur, A. (2021). When the tab comes due: challenges in the cost structure of browser tab usage. *CHI '21*. https://doi.org/10.1145/3411764.3445585

Chi, E. H., Pirolli, P., Chen, K., & Pitkow, J. (2001). Using information scent to model user information needs and actions on the Web. *CHI '01*. https://doi.org/10.1145/365024.365325

Cockburn, A., Gutwin, C., Scarr, J., & Malacria, S. (2014). Supporting novice to expert transitions in user interfaces. *ACM Computing Surveys*, 47(2). https://doi.org/10.1145/2659796

Cockburn, A., Karlson, A., & Bederson, B. B. (2008). A review of overview+detail, zooming, and focus+context interfaces. *ACM Computing Surveys*, 41(1). https://doi.org/10.1145/1456650.1456652

Cockburn, A., & McKenzie, B. (2001). What do web users do? *IJHCS*, 54(6), 903–922. https://doi.org/10.1006/ijhc.2001.0459

Cowan, N. (2001). The magical number 4 in short-term memory. *Behavioral and Brain Sciences*, 24(1), 87–114. https://doi.org/10.1017/S0140525X01003922

Craft, B., & Cairns, P. (2005). Beyond guidelines: what can we learn from the visual information seeking mantra? *IV 2005*. https://doi.org/10.1109/IV.2005.28

Czerwinski, M., Horvitz, E., & Wilhite, S. (2004). A diary study of task switching and interruptions. *CHI '04*. https://doi.org/10.1145/985692.985715

Deci, E. L., Koestner, R., & Ryan, R. M. (1999). A meta-analytic review of experiments examining the effects of extrinsic rewards on intrinsic motivation. *Psychological Bulletin*, 125(6), 627–668. https://doi.org/10.1037/0033-2909.125.6.627

Dubroy, P., & Balakrishnan, R. (2010). A study of tabbed browsing among Mozilla Firefox users. *CHI '10*. https://doi.org/10.1145/1753326.1753426

Dyson, M. C. (2004). How physical text layout affects reading from screen. *BIT*, 23(6), 377–393. https://doi.org/10.1080/01449290410001715714

Dyson, M. C., & Haselgrove, M. (2001). The influence of reading speed and line length on the effectiveness of reading from screen. *IJHCS*, 54(4), 585–612. https://doi.org/10.1006/ijhc.2001.0458

Endsley, M. R. (1995). Toward a theory of situation awareness in dynamic systems. *Human Factors*, 37(1), 32–64. https://doi.org/10.1518/001872095779049543

Endsley, M. R., & Kiris, E. O. (1995). The out-of-the-loop performance problem and level of control in automation. *Human Factors*, 37(2), 381–394. https://doi.org/10.1518/001872095779064555

Faulkner, L. (2003). Beyond the five-user assumption. *Behavior Research Methods*, 35(3), 379–383. https://doi.org/10.3758/BF03195514

Findlater, L., & McGrenere, J. (2004). A comparison of static, adaptive, and adaptable menus. *CHI '04*. https://doi.org/10.1145/985692.985704

Findlater, L., & McGrenere, J. (2008). Impact of screen size on performance, awareness, and user satisfaction with adaptive GUIs. *CHI '08*. https://doi.org/10.1145/1357054.1357249

Findlater, L., & McGrenere, J. (2010). Beyond performance: feature awareness in personalized interfaces. *IJHCS*, 68(3), 121–137. https://doi.org/10.1016/j.ijhcs.2009.10.002

Findlater, L., Moffatt, K., McGrenere, J., & Dawson, J. (2009). Ephemeral adaptation: the use of gradual onset to improve menu selection performance. *CHI '09*. https://doi.org/10.1145/1518701.1518956

Fitts, P. M. (1954). The information capacity of the human motor system in controlling the amplitude of movement. *J. Exp. Psychol.*, 47(6), 381–391. https://doi.org/10.1037/h0055392

Fitz, N., et al. (2019). Batching smartphone notifications can improve well-being. *Computers in Human Behavior*, 101, 84–94. https://doi.org/10.1016/j.chb.2019.07.016

Fürhoff, L. (2019). Rethinking the usage and experience of clustering markers in web mapping. *PeerJ Preprints* 7:e27858. https://peerj.com/preprints/27858

Gajos, K. Z., Czerwinski, M., Tan, D. S., & Weld, D. S. (2006). Exploring the design space for adaptive graphical user interfaces. *AVI '06*. https://doi.org/10.1145/1133265.1133306

Gajos, K. Z., Everitt, K., Tan, D. S., Czerwinski, M., & Weld, D. S. (2008). Predictability and accuracy in adaptive user interfaces. *CHI '08*. https://doi.org/10.1145/1357054.1357252

Gernsbacher, M. A. (2015). Video captions benefit everyone. *Policy Insights from the Behavioral and Brain Sciences*, 2(1), 195–202. https://doi.org/10.1177/2372732215602130

Goddard, K., Roudsari, A., & Wyatt, J. C. (2012). Automation bias: a systematic review. *JAMIA*, 19(1), 121–127. https://doi.org/10.1136/amiajnl-2011-000089

Gołębiowska, I. (2015; online 2013). Legend layouts for thematic maps: a case study integrating usability metrics with the thinking aloud method. *The Cartographic Journal*, 52(1). https://doi.org/10.1179/1743277413Y.0000000045

González, V. M., & Mark, G. (2004). "Constant, constant, multi-tasking craziness": managing multiple working spheres. *CHI '04*. https://doi.org/10.1145/985692.985707

Gray, C. M., Kou, Y., Battles, B., Hoggatt, J., & Toombs, A. L. (2018). The dark (patterns) side of UX design. *CHI '18*. https://doi.org/10.1145/3173574.3174108

Gray, W. D., & Salzman, M. C. (1998). Damaged merchandise? *Human-Computer Interaction*, 13(3), 203–261. https://doi.org/10.1207/s15327051hci1303_2

Griffin, A. L., MacEachren, A. M., Hardisty, F., Steiner, E., & Li, B. (2006). A comparison of animated maps with static small-multiple maps for visually identifying space-time clusters. *Annals AAG*, 96(4), 740–753. https://doi.org/10.1111/j.1467-8306.2006.00514.x

Grossman, T., & Fitzmaurice, G. (2010). ToolClips: an investigation of contextual video assistance for functionality understanding. *CHI '10*. https://doi.org/10.1145/1753326.1753552

Grossman, T., Fitzmaurice, G., & Attar, R. (2009). A survey of software learnability. *CHI '09*. https://doi.org/10.1145/1518701.1518803

Grudin, J. (1989). The case against user interface consistency. *CACM*, 32(10), 1164–1173. https://doi.org/10.1145/67933.67934

Hamari, J., Koivisto, J., & Sarsa, H. (2014). Does gamification work? *HICSS 2014*. https://doi.org/10.1109/HICSS.2014.377

Harrison, C., Amento, B., Kuznetsov, S., & Bell, R. (2007). Rethinking the progress bar. *UIST '07*. https://doi.org/10.1145/1294211.1294231

Harrower, M. (2007). The cognitive limits of animated maps. *Cartographica*, 42(4), 349–357. https://doi.org/10.3138/carto.42.4.349

Hart, S. G. (2006). NASA-Task Load Index (NASA-TLX); 20 years later. *Proc. HFES*, 50(9). https://doi.org/10.1177/154193120605000909

Hart, S. G., & Staveland, L. E. (1988). Development of NASA-TLX. *Advances in Psychology*, 52, 139–183. https://doi.org/10.1016/S0166-4115(08)62386-9

Hassenzahl, M., & Monk, A. (2010). The inference of perceived usability from beauty. *Human-Computer Interaction*, 25(3), 235–260. https://doi.org/10.1080/07370024.2010.500139

Heer, J., & Robertson, G. (2007). Animated transitions in statistical data graphics. *IEEE TVCG*, 13(6), 1240–1247. https://doi.org/10.1109/TVCG.2007.70539

Henderson, D. A., & Card, S. (1986). Rooms: the use of multiple virtual workspaces to reduce space contention. *ACM TOG*, 5(3), 211–243. https://doi.org/10.1145/24054.24056

Henderson, J. M., Chanceaux, M., & Smith, T. J. (2009). The influence of clutter on real-world scene search. *Journal of Vision*, 9(1):32. https://doi.org/10.1167/9.1.32

Hertzum, M., Hansen, K. D., & Andersen, H. H. K. (2009). Scrutinising usability evaluation: does thinking aloud affect behaviour and mental workload? *BIT*, 28(2), 165–181. https://doi.org/10.1080/01449290701773842

Hertzum, M., & Jacobsen, N. E. (2001). The evaluator effect. *IJHCI*, 13(4), 421–443. https://doi.org/10.1207/S15327590IJHC1304_05

Hill, W. C., Hollan, J. D., Wroblewski, D., & McCandless, T. (1992). Edit wear and read wear. *CHI '92*. https://doi.org/10.1145/142750.142751

Hollender, N., Hofmann, C., Deneke, M., & Schmitz, B. (2010). Integrating cognitive load theory and concepts of human–computer interaction. *Computers in Human Behavior*, 26(6), 1278–1288. https://doi.org/10.1016/j.chb.2010.05.031

Hornbæk, K., Bederson, B. B., & Plaisant, C. (2002). Navigation patterns and usability of zoomable user interfaces with and without an overview. *ACM TOCHI*, 9(4), 362–389. https://doi.org/10.1145/586081.586086

Hornbæk, K., & Hertzum, M. (2011). The notion of overview in information visualization. *IJHCS*, 69(7–8), 509–525. https://doi.org/10.1016/j.ijhcs.2011.02.007

Horvitz, E. (1999). Principles of mixed-initiative user interfaces. *CHI '99*. https://doi.org/10.1145/302979.303030

Huang, J., & White, R. W. (2010). Parallel browsing behavior on the web. *Hypertext '10*. https://doi.org/10.1145/1810617.1810622

Hutchings, D. R., Smith, G., Meyers, B., Czerwinski, M., & Robertson, G. (2004). Display space usage and window management operation comparisons between single monitor and multiple monitor users. *AVI '04*. https://doi.org/10.1145/989863.989867

Hwang, W., & Salvendy, G. (2010). Number of people required for usability evaluation: the 10±2 rule. *CACM*, 53(5), 130–133. https://doi.org/10.1145/1735223.1735255

Inbar, O., Tractinsky, N., & Meyer, J. (2007). Minimalism in information visualization: attitudes towards maximizing the data-ink ratio. *ECCE '07*. https://doi.org/10.1145/1362550.1362587

Iqbal, S. T., & Bailey, B. P. (2008). Effects of intelligent notification management on users and their tasks. *CHI '08*. https://doi.org/10.1145/1357054.1357070

Iqbal, S. T., & Horvitz, E. (2007). Disruption and recovery of computing tasks: field study, analysis, and directions. *CHI '07*. https://doi.org/10.1145/1240624.1240730

Iqbal, S. T., & Horvitz, E. (2010). Notifications and awareness: a field study of alert usage and preferences. *CSCW '10*. https://doi.org/10.1145/1718918.1718926

Isherwood, S. J., McDougall, S. J. P., & Curry, M. B. (2007). Icon identification in context. *Human Factors*, 49(3), 465–476. https://doi.org/10.1518/001872007X200102

ISO 9186-1:2014. Graphical symbols — Test methods — Part 1: Method for testing comprehensibility. https://www.iso.org/standard/59226.html

ISO 9241-110:2020. Ergonomics of human-system interaction — Part 110: Interaction principles.

ISO 9241-112:2017. Part 112: Principles for the presentation of information.

ISO 9241-125:2017. Part 125: Guidance on visual presentation of information.

ISO 9241-143:2012. Part 143: Forms.

Jachimowicz, J. M., Duncan, S., Weber, E. U., & Johnson, E. J. (2019). When and why defaults influence decisions: a meta-analysis of default effects. *Behavioural Public Policy*, 3(2), 159–186. https://doi.org/10.1017/bpp.2018.43

Jeffries, R., Miller, J. R., Wharton, C., & Uyeda, K. (1991). User interface evaluation in the real world: a comparison of four techniques. *CHI '91*. https://doi.org/10.1145/108844.108862

Johnson, E. J., & Goldstein, D. (2003). Do defaults save lives? *Science*, 302, 1338–1339. https://doi.org/10.1126/science.1091721

Jones, W., Bruce, H., & Dumais, S. (2001). Keeping found things found on the web. *CIKM '01*. https://doi.org/10.1145/502585.502607

Kalyuga, S., Ayres, P., Chandler, P., & Sweller, J. (2003). The expertise reversal effect. *Educational Psychologist*, 38(1), 23–31. https://doi.org/10.1207/S15326985EP3801_4

Kalyuga, S., Chandler, P., & Sweller, J. (1998). Levels of expertise and instructional design. *Human Factors*, 40(1), 1–17. https://doi.org/10.1518/001872098779480587

Kandogan, E., & Shneiderman, B. (1997). Elastic Windows: evaluation of multi-window operations. *CHI '97*. https://doi.org/10.1145/258549.258720

Kelleher, C., & Pausch, R. (2005). Stencils-based tutorials: design and evaluation. *CHI '05*. https://doi.org/10.1145/1054972.1055047

Kendrick, A. (2020). Mobile tutorials: wasted effort or efficiency boost? *Nielsen Norman Group*. https://www.nngroup.com/articles/mobile-tutorials/

Kiger, J. I. (1984). The depth/breadth trade-off in the design of menu-driven user interfaces. *IJMMS*, 20(2), 201–213. https://doi.org/10.1016/S0020-7373(84)80018-8

Korpi, J., & Ahonen-Rainio, P. (2013). Clutter reduction methods for point symbols in map mashups. *The Cartographic Journal*, 50(3), 257–265. https://doi.org/10.1179/1743277413Y.0000000065

Lam, H., Bertini, E., Isenberg, P., Plaisant, C., & Carpendale, S. (2012). Empirical studies in information visualization: seven scenarios. *IEEE TVCG*, 18(9). https://doi.org/10.1109/TVCG.2011.279

Lane, D. M., Napier, H. A., Peres, S. C., & Sándor, A. (2005). Hidden costs of graphical user interfaces. *IJHCI*, 18(2), 133–144. https://doi.org/10.1207/s15327590ijhc1802_1

Larson, K., & Czerwinski, M. (1998). Web page design: implications of memory, structure and scent for information retrieval. *CHI '98*. https://doi.org/10.1145/274644.274649

Lee, J. D., & See, K. A. (2004). Trust in automation: designing for appropriate reliance. *Human Factors*, 46(1), 50–80. https://doi.org/10.1518/hfes.46.1.50_30392

Lewis, J. R. (2018). The System Usability Scale: past, present, and future. *IJHCI*, 34(7), 577–590. https://doi.org/10.1080/10447318.2018.1455307

Lewis, J. R., Utesch, B. S., & Maher, D. E. (2013). UMUX-LITE: when there's no time for the SUS. *CHI '13*. https://doi.org/10.1145/2470654.2481287

Lindgaard, G., Fernandes, G., Dudek, C., & Brown, J. (2006). Attention web designers: you have 50 milliseconds to make a good first impression! *BIT*, 25(2), 115–126. https://doi.org/10.1080/01449290500330448

Liu, Z., & Heer, J. (2014). The effects of interactive latency on exploratory visual analysis. *IEEE TVCG*, 20(12), 2122–2131. https://doi.org/10.1109/TVCG.2014.2346452

Lorch, R. F. (1989). Text-signaling devices and their effects on reading and memory processes. *Educational Psychology Review*, 1(3), 209–234. https://doi.org/10.1007/BF01320135

Luguri, J., & Strahilevitz, L. J. (2021). Shining a light on dark patterns. *Journal of Legal Analysis*, 13(1), 43–109. https://doi.org/10.1093/jla/laaa006

Mackay, W. E. (1991). Triggers and barriers to customizing software. *CHI '91*. https://doi.org/10.1145/108844.108867

Mahatody, T., Sagar, M., & Kolski, C. (2010). State of the art on the cognitive walkthrough method, its variants and evolutions. *IJHCI*, 26(8), 741–785. https://doi.org/10.1080/10447311003781409

Mandler, G. (1980). Recognizing: the judgment of previous occurrence. *Psychological Review*, 87(3), 252–271. https://doi.org/10.1037/0033-295X.87.3.252

Mark, G., Gudith, D., & Klocke, U. (2008). The cost of interrupted work: more speed and stress. *CHI '08*. https://doi.org/10.1145/1357054.1357072

Marshall, C. C. (1997). Annotation: from paper books to the digital library. *DL '97*. https://doi.org/10.1145/263690.263806

Marshall, C. C. (1998). Toward an ecology of hypertext annotation. *Hypertext '98*. https://doi.org/10.1145/276627.276632

Marshall, C. C., & Brush, A. J. B. (2004). Exploring the relationship between personal and public annotations. *JCDL '04*. https://doi.org/10.1145/996350.996432

Mathur, A., et al. (2019). Dark patterns at scale. *PACM HCI (CSCW)*, 3. https://doi.org/10.1145/3359183

McDougall, S. J. P., Curry, M. B., & de Bruijn, O. (1999). Measuring symbol and icon characteristics: norms for 239 symbols. *Behavior Research Methods*, 31(3), 487–519. https://doi.org/10.3758/BF03200730

McDougall, S. J. P., de Bruijn, O., & Curry, M. B. (2000). Exploring the effects of icon characteristics on user performance. *J. Exp. Psychol.: Applied*, 6(4), 291–306. https://doi.org/10.1037/1076-898X.6.4.291

McFarlane, D. C. (2002). Comparison of four primary methods for coordinating the interruption of people in human-computer interaction. *Human-Computer Interaction*, 17(1), 63–139. https://doi.org/10.1207/S15327051HCI1701_2

McGrenere, J., Baecker, R. M., & Booth, K. S. (2002). An evaluation of a multiple interface design solution for bloated software. *CHI '02*. https://doi.org/10.1145/503376.503406

Meier, S. (2016). The marker cluster: a critical analysis and a new approach to a common web-based cartographic interface pattern. *IJAEIS*, 7(1), 28–43. https://www.igi-global.com/article/the-marker-cluster/153624/

Michalski, R., Grobelny, J., & Karwowski, W. (2006). The effects of graphical interface design characteristics on human–computer interaction task efficiency. *Int. J. Industrial Ergonomics*, 36(11), 959–977. https://doi.org/10.1016/j.ergon.2006.06.013

Miller, G. A. (1956). The magical number seven, plus or minus two. *Psychological Review*, 63(2), 81–97. https://doi.org/10.1037/h0043158

Moran, K. (2017). Flat UI elements attract less attention and cause uncertainty. *Nielsen Norman Group*. https://www.nngroup.com/articles/flat-ui-less-attention-cause-uncertainty/

Morkes, J., & Nielsen, J. (1997). Concise, SCANNABLE, and objective: how to write for the web. *Nielsen Norman Group*. https://www.nngroup.com/articles/concise-scannable-and-objective-how-to-write-for-the-web/

Myers, B. A. (1985). The importance of percent-done progress indicators for computer-human interfaces. *CHI '85*. https://doi.org/10.1145/317456.317459

Nielsen, J., & Landauer, T. K. (1993). A mathematical model of the finding of usability problems. *INTERCHI '93*. https://doi.org/10.1145/169059.169166

Nielsen, J., & Molich, R. (1990). Heuristic evaluation of user interfaces. *CHI '90*. https://doi.org/10.1145/97243.97281

Norman, D. A. (1999). Affordance, conventions, and design. *interactions*, 6(3), 38–43. https://doi.org/10.1145/301153.301168

North, C., & Shneiderman, B. (2000). Snap-together visualization: can users construct and operate coordinated visualizations? *IJHCS*, 53(5), 715–739. https://doi.org/10.1006/ijhc.2000.0418

Nowell, L., Hetzler, E., & Tanasse, T. (2001). Change blindness in information visualization: a case study. *InfoVis 2001*. https://doi.org/10.1109/INFVIS.2001.963274

Obendorf, H., Weinreich, H., Herder, E., & Mayer, M. (2007). Web page revisitation revisited. *CHI '07*. https://doi.org/10.1145/1240624.1240719

O'Hara, K., & Sellen, A. (1997). A comparison of reading paper and on-line documents. *CHI '97*. https://doi.org/10.1145/258549.258787

Onnasch, L., Wickens, C. D., Li, H., & Manzey, D. (2014). Human performance consequences of stages and levels of automation: an integrated meta-analysis. *Human Factors*, 56(3), 476–488. https://doi.org/10.1177/0018720813501549

Ozok, A. A., & Salvendy, G. (2000). Measuring consistency of web page design and its effects on performance and satisfaction. *Ergonomics*, 43(4), 443–460. https://doi.org/10.1080/001401300184332

Page, S. R., Johnsgard, T. J., Albert, U., & Allen, C. D. (1996). User customization of a word processor. *CHI '96*. https://doi.org/10.1145/238386.238541

Palmer, S. E. (1992). Common region: a new principle of perceptual grouping. *Cognitive Psychology*, 24(3), 436–447. https://doi.org/10.1016/0010-0285(92)90014-S

Palmiter, S., & Elkerton, J. (1993). Animated demonstrations for learning procedural computer-based tasks. *Human-Computer Interaction*, 8(3), 193–216. https://doi.org/10.1207/s15327051hci0803_1

Parasuraman, R., & Riley, V. (1997). Humans and automation: use, misuse, disuse, abuse. *Human Factors*, 39(2), 230–253. https://doi.org/10.1518/001872097778543886

Parasuraman, R., Sheridan, T. B., & Wickens, C. D. (2000). A model for types and levels of human interaction with automation. *IEEE Trans. SMC-A*, 30(3), 286–297. https://doi.org/10.1109/3468.844354

Pernice, K., & Budiu, R. (2016). Hamburger menus and hidden navigation hurt UX metrics. *Nielsen Norman Group*. https://www.nngroup.com/articles/hamburger-menus/

Pirolli, P., & Card, S. (1999). Information foraging. *Psychological Review*, 106(4), 643–675. https://doi.org/10.1037/0033-295X.106.4.643

Plumlee, M. D., & Ware, C. (2006). Zooming versus multiple window interfaces: cognitive costs of visual comparisons. *ACM TOCHI*, 13(2), 179–209. https://doi.org/10.1145/1165734.1165736

Polson, P. G., Muncher, E., & Engelbeck, G. (1986). A test of a common elements theory of transfer. *CHI '86*. https://doi.org/10.1145/22627.22352

Rello, L., Pielot, M., & Marcos, M.-C. (2016). Make it big! The effect of font size and line spacing on online readability. *CHI '16*. https://doi.org/10.1145/2858036.2858204

Rensink, R. A., O'Regan, J. K., & Clark, J. J. (1997). To see or not to see: the need for attention to perceive changes in scenes. *Psychological Science*, 8(5), 368–373. https://doi.org/10.1111/j.1467-9280.1997.tb00427.x

Roberts, J. C. (2007). State of the art: coordinated & multiple views in exploratory visualization. *CMV 2007*. https://doi.org/10.1109/CMV.2007.20

Robertson, G., Fernandez, R., Fisher, D., Lee, B., & Stasko, J. (2008). Effectiveness of animation in trend visualization. *IEEE TVCG*, 14(6), 1325–1332. https://doi.org/10.1109/TVCG.2008.125

Rosenholtz, R., Li, Y., Mansfield, J., & Jin, Z. (2005). Feature congestion: a measure of display clutter. *CHI '05*. https://doi.org/10.1145/1054972.1055078

Rosenholtz, R., Li, Y., & Nakano, L. (2007). Measuring visual clutter. *Journal of Vision*, 7(2):17. https://doi.org/10.1167/7.2.17

Roth, R. E. (2013). Interactive maps: what we know and what we need to know. *JOSIS*, 6, 59–115. https://doi.org/10.5311/JOSIS.2013.6.105

Satzinger, J. W., & Olfman, L. (1998). User interface consistency across end-user applications: the effects on mental models. *Journal of Management Information Systems*, 14(4), 167–193. (DOI not checked)

Scarr, J., Cockburn, A., Gutwin, C., & Quinn, P. (2011). Dips and ceilings: understanding and supporting transitions to expertise in user interfaces. *CHI '11*. https://doi.org/10.1145/1978942.1979348

Schilit, B. N., Golovchinsky, G., & Price, M. N. (1998). Beyond paper: supporting active reading with free form digital ink annotations. *CHI '98*. https://doi.org/10.1145/274644.274680

Schmutz, S., Sonderegger, A., & Sauer, J. (2016). Implementing recommendations from Web accessibility guidelines: would they also provide benefits to nondisabled users? *Human Factors*, 58(4), 611–629. https://doi.org/10.1177/0018720816640962

Sears, A., & Shneiderman, B. (1994). Split menus: effectively using selection frequency to organize menus. *ACM TOCHI*, 1(1), 27–51. https://doi.org/10.1145/174630.174632

Shah, R. C., & Sandvig, C. (2008). Software defaults as de facto regulation: the case of the wireless internet. *Information, Communication & Society*, 11(1), 25–46. https://doi.org/10.1080/13691180701858836

Shneiderman, B. (1996). The eyes have it: a task by data type taxonomy for information visualizations. *IEEE Symposium on Visual Languages*. https://doi.org/10.1109/VL.1996.545307

Shneiderman, B. (2003). Promoting universal usability with multi-layer interface design. *CUU '03*. https://doi.org/10.1145/957205.957206

Shulman, H. C., Dixon, G. N., Bullock, O. M., & Colón Amill, D. (2020). The effects of jargon on processing fluency, self-perceptions, and scientific engagement. *J. Language and Social Psychology*, 39(5–6), 579–602. https://doi.org/10.1177/0261927X20902177

Simons, D. J., & Rensink, R. A. (2005). Change blindness: past, present, and future. *Trends in Cognitive Sciences*, 9(1), 16–20. https://doi.org/10.1016/j.tics.2004.11.006

Spencer, R. (2000). The streamlined cognitive walkthrough method. *CHI '00*. https://doi.org/10.1145/332040.332456

Spool, J., & Schroeder, W. (2001). Testing web sites: five users is nowhere near enough. *CHI '01 EA*. https://doi.org/10.1145/634067.634236

Sweller, J. (1988). Cognitive load during problem solving: effects on learning. *Cognitive Science*, 12(2), 257–285. https://doi.org/10.1207/s15516709cog1202_4

Tauscher, L., & Greenberg, S. (1997). How people revisit web pages. *IJHCS*, 47(1), 97–137. https://doi.org/10.1006/ijhc.1997.0125

Teevan, J., Adar, E., Jones, R., & Potts, M. (2007). Information re-retrieval: repeat queries in Yahoo's logs. *SIGIR '07*. https://doi.org/10.1145/1277741.1277770

Teevan, J., Alvarado, C., Ackerman, M. S., & Karger, D. R. (2004). The perfect search engine is not enough: a study of orienteering behavior in directed search. *CHI '04*. https://doi.org/10.1145/985692.985745

Tractinsky, N., Katz, A. S., & Ikar, D. (2000). What is beautiful is usable. *Interacting with Computers*, 13(2), 127–145. https://doi.org/10.1016/S0953-5438(00)00031-X

Treisman, A. M., & Gelade, G. (1980). A feature-integration theory of attention. *Cognitive Psychology*, 12(1), 97–136. https://doi.org/10.1016/0010-0285(80)90005-5

Tuch, A. N., Roth, S. P., Hornbæk, K., Opwis, K., & Bargas-Avila, J. A. (2012). Is beautiful really usable? *Computers in Human Behavior*, 28(5), 1596–1607. https://doi.org/10.1016/j.chb.2012.03.024

Tullis, T. S. (1983). The formatting of alphanumeric displays: a review and analysis. *Human Factors*, 25(6), 657–682. https://doi.org/10.1177/001872088302500604

Tullis, T. S., & Stetson, J. N. (2004). A comparison of questionnaires for assessing website usability. *UPA 2004 Conference* (not journal peer-reviewed).

Tversky, B., Morrison, J. B., & Bétrancourt, M. (2002). Animation: can it facilitate? *IJHCS*, 57(4), 247–262. https://doi.org/10.1006/ijhc.2002.1017

Vermeulen, J., Luyten, K., van den Hoven, E., & Coninx, K. (2013). Crossing the bridge over Norman's gulf of execution: revealing feedforward's true identity. *CHI '13*. https://doi.org/10.1145/2470654.2466255

W3C (2023, updated 2024). Web Content Accessibility Guidelines (WCAG) 2.2. https://www.w3.org/TR/WCAG22/

Wagemans, J., et al. (2012). A century of Gestalt psychology in visual perception: I. Perceptual grouping and figure–ground organization. *Psychological Bulletin*, 138(6), 1172–1217. https://doi.org/10.1037/a0029333

Wang Baldonado, M. Q., Woodruff, A., & Kuchinsky, A. (2000). Guidelines for using multiple views in information visualization. *AVI '00*. https://doi.org/10.1145/345513.345271

Weinreich, H., Obendorf, H., Herder, E., & Mayer, M. (2008). Not quite the average: an empirical study of Web use. *ACM TWeb*, 2(1). https://doi.org/10.1145/1326561.1326566

Wharton, C., Rieman, J., Lewis, C., & Polson, P. (1994). The cognitive walkthrough method: a practitioner's guide. In Nielsen & Mack (Eds.), *Usability Inspection Methods*. Wiley.

Whittaker, S., Matthews, T., Cerruti, J., Badenes, H., & Tang, J. (2011). Am I wasting my time organizing email? A study of email refinding. *CHI '11*. https://doi.org/10.1145/1978942.1979457

Wiedenbeck, S. (1999). The use of icons and labels in an end user application program: an empirical study of learning and retention. *BIT*, 18(2), 68–82. https://doi.org/10.1080/014492999119129

Wolfe, J. M. (1998). What can 1 million trials tell us about visual search? *Psychological Science*, 9(1), 33–39. https://doi.org/10.1111/1467-9280.00006

Wolfe, J. M., & Horowitz, T. S. (2017). Five factors that guide attention in visual search. *Nature Human Behaviour*, 1, 0058. https://doi.org/10.1038/s41562-017-0058
