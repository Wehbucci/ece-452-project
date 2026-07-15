# Grasp — Architecture & Contributor Guide

This is the map of the codebase. Read it before adding a screen or feature so everything stays
consistent. For *what* we're building and *why*, see [`../overview.md`](../overview.md); this
doc covers *how the code is organized*.

> **Status:** frontend skeleton. The screens, navigation and design system are real; the data
> is served by an in-memory fake ([`FakePathRepository`](#data-layer)). No network, no AI, no
> persistence yet — those slot in behind the existing seams without changing the UI.

---

## Architecture at a glance — MVP

We use **MVP (Model–View–Presenter)** (required by NFR 5.1). Mapped onto Jetpack Compose:

| Layer | What it is | Where | Rule |
|---|---|---|---|
| **View** | A Composable screen | `ui/feature/<x>/XxxScreen.kt` | No business logic. Renders state, forwards events. |
| **Presenter** | Plain Kotlin class | `ui/feature/<x>/XxxPresenter.kt` | All logic. **No Compose/Android imports** → unit-testable. |
| **Model** | Data classes + repositories | `data/` | Data access only. |

The View↔Presenter boundary is an explicit interface defined per screen in `XxxContract.kt`.
The base interfaces (`MvpView`, `MvpPresenter`, `BasePresenter`) and the full rationale live in
[`core/mvp/Mvp.kt`](app/src/main/java/com/example/grasp/core/mvp/Mvp.kt) — **start there.**

```
   View (Composable)  ──user events──▶  Presenter (logic)  ──asks──▶  Model (repository)
          ▲                                   │
          └──────── view.showX(...) ──────────┘   (Presenter pushes results back)
```

---

## Package layout

```
com.example.grasp
├─ MainActivity.kt            # tiny: applies theme, launches the nav graph
│
├─ core/mvp/                  # MVP base contracts (MvpView, MvpPresenter, BasePresenter)
│
├─ data/                      # MODEL layer
│  ├─ model/                  # domain data classes (TreeNode, Subtopic, TinkerStep, …)
│  └─ repository/             # PathRepository (interface) + FakePathRepository (sample data)
│
└─ ui/
   ├─ theme/                  # design system: Color.kt, Type.kt, Theme.kt
   ├─ components/             # shared dumb Composables (bottom bar, progress, pills, …)
   ├─ navigation/             # GraspDestinations (routes) + GraspNavHost (the graph)
   └─ feature/                # one package per screen, each = Contract + Presenter + Screen
      ├─ auth/                #   Login  ← canonical MVP example, read this one first
      ├─ home/                #   Topic entry + mode picker + popular topics
      ├─ path/                #   Learner roadmap: list + Compose-Canvas TREE (TreeCanvas.kt)
      ├─ subtopic/            #   Subtopic detail: content blocks, resources, "Ask AI"
      ├─ tinker/              #   Tinkerer step-by-step checklist
      ├─ chat/                #   Multi-modal AI chat
      ├─ library/             #   Saved paths & guides (resume / remove)
      └─ profile/             #   Account + settings placeholders + logout
```

---

## Navigation

Single-activity, single `NavHost`. Everything about routing is in
[`ui/navigation/`](app/src/main/java/com/example/grasp/ui/navigation):

- **`GraspDestinations`** — every route string + type-safe builders (`GraspDestinations.path(id)`).
  Never hand-build a route at a call site.
- **`GraspNavHost`** — the graph. The **only** place that holds a `NavController`. Screens
  receive plain lambdas (`onOpenSubtopic`, `onBack`, …) so they stay decoupled and previewable.

Flow: `login → {home, library, profile} → path/tinker → subtopic → chat`. The bottom nav bar
shows only on the three top-level tabs.

---

## Design system (why it looks the way it does)

The product is about **protecting attention**, so the UI is deliberately calm and uncluttered:

- **One accent** (indigo) for primary actions and the "current" item — the eye always knows
  where to go next.
- **Color is semantic** and matches the tree-node states in overview.md §8:
  🟢 green = completed · 🔵 indigo = active/current · ⚪ grey = unexplored · 🟠 amber dashed = "branch out".
- Soft near-white background for long, comfortable reading sessions.

All colors are tokens in [`ui/theme/Color.kt`](app/src/main/java/com/example/grasp/ui/theme/Color.kt)
— **never hard-code a hex value in a Composable.** Type scale is in `Type.kt`. Dynamic
(wallpaper) color is intentionally **off** to keep the brand consistent (see `Theme.kt`).

---

## How to add a new screen (the recipe)

1. Create `ui/feature/<name>/`.
2. **`<Name>Contract.kt`** — define `interface View : MvpView { … }` (what the presenter can
   show / navigate) and `interface Presenter : MvpPresenter<View> { … }` (user events).
3. **`<Name>Presenter.kt`** — `class <Name>Presenter(deps…) : BasePresenter<….View>(), ….Presenter`.
   Put logic here. Load initial data in `onViewAttached()`. **No Compose imports.**
4. **`<Name>Screen.kt`** — copy the wiring block from
   [`LoginScreen`](app/src/main/java/com/example/grasp/ui/feature/auth/LoginScreen.kt)
   (UI state → presenter → anonymous View → `DisposableEffect` attach/detach). Build the UI
   from `ui/components` + theme tokens.
5. Register a route in `GraspDestinations`, add a `composable(...)` in `GraspNavHost`, and pass
   navigation lambdas in.

> Tip: name navigation lambdas (`onOpenX`, `navigateToX`) **differently** from the View's
> methods (`openX`, `onLoggedIn`) — identical names shadow each other and cause recursion.

---

## Data layer

[`PathRepository`](app/src/main/java/com/example/grasp/data/repository/PathRepository.kt) is the
seam presenters depend on. Today it's implemented by
[`FakePathRepository`](app/src/main/java/com/example/grasp/data/repository/FakePathRepository.kt)
(in-memory, sample data from the overview.md "Jordan" scenarios).

When the real backend lands: implement the same interface with cloud + local cache, make the
methods `suspend` (coroutines, per overview.md §4), return `Result` types for graceful errors
(NFR 3.1), and inject it instead of the `object` singleton. **UI and presenters shouldn't need
to change.** Remember: the tree JSON stays lightweight; node content is fetched lazily via
`contentRef` (overview.md §5), and the AI key never ships in the client (NFR 4.3).

---

## Tech stack

Kotlin · Jetpack Compose (Material 3, no XML layouts) · Navigation-Compose · MVP.
`minSdk 26 · targetSdk/compileSdk 37 · Java 11`. Dependencies go through the version catalog
(`gradle/libs.versions.toml`) via `libs.*` aliases — no hard-coded version strings.
