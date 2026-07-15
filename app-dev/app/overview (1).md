# Grasp — Project Specification

> **Purpose of this file:** A single reference document for an AI coding agent.
> It consolidates everything decided about the project so the agent can write
> code that is consistent with the team's intent. Read this before generating
> or modifying any code.

---

## 1. Project Identity

| Field | Value |
|---|---|
| **App name** | Grasp |
| **Package / namespace** | `com.example.grasp` |
| **Application ID** | `com.example.grasp` |
| **Team** | Null Pointers |
| **Course** | ECE 452 (Software Design & Architecture) |
| **Proposal version** | 1.0 — June 05, 2026 |
| **Platform** | Android only (native) |

> Note: The proposal mock-ups use the placeholder name "Pathly". The decided
> product name is **Grasp**, reflected in the package name. Use "Grasp" everywhere.

---

## 2. One-Paragraph Overview

Grasp is a mobile (Android) learning app that turns any topic or task into a
structured, easy-to-follow path. A user enters what they want to learn, and the
app uses AI to break it into ordered subtopics arranged as a **learning tree
(roadmap)**. Each subtopic has generated content (a short intro/excerpt plus
deeper material), resource links (articles, books), and an inline **multi-modal
AI chat assistant**. The goal is to solve the "where do I even start?" problem
by replacing one long messy stream of AI text (like a raw ChatGPT conversation)
with a clear, navigable structure where progress is always tracked.

---

## 3. The Two Modes (core product concept)

The app has two primary user-facing modes, mapped to two actor types:

### Learner Mode
- For **learning a topic** (e.g. "machine learning", "cooking for beginners").
- Generates a **branching learning tree / roadmap** that splits the topic into
  subtopics in a logical order.
- Subtopics can be viewed as a **visual tree** OR a **standard list**.
- Progress is shown as **node states** in tree view and a **progress bar** in list view.

### Tinkerer Mode
- For **accomplishing a concrete task** (e.g. "make a breakfast omelette",
  "repair X", build something).
- Generates a **flat, ordered, step-by-step guide** with checkboxes.
- Progress is shown as a **step checklist with a linear progress bar**.
- Actor examples: chef, repair-person, builder, experimenter.

Both modes share: AI content generation, the multi-modal AI chat, save/resume,
preference-based generation, and progress tracking.

---

## 4. Tech Stack & Build Configuration

> These are confirmed decisions. The agent should target exactly these.

- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose (Material 3). No XML layouts. The learning tree
  (Figure 2) should be built with Compose Canvas APIs.
- **Architecture:** **MVP** (Model–View–Presenter). *(This satisfies NFR 5.1,
  which required a defined GUI architecture among MVC / MVP / MVVM.)*
- **Project base:** started from Android Studio **Empty Activity** template.
- **Navigation:** add the Jetpack Navigation Component (`androidx.navigation`) manually.

### Gradle module config (`app/build.gradle.kts`) — current source of truth

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.grasp"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    defaultConfig {
        applicationId = "com.example.grasp"
        minSdk = 26          // Android 8.0 — covers ~95%+ of active devices
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release { optimization { enable = false } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}
```

**Key constraints for the agent:**
- `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37`.
- Java 11 source/target compatibility.
- Dependencies are managed via a **version catalog** (`gradle/libs.versions.toml`).
  Reference libraries through the `libs.*` aliases (see catalog), not hardcoded
  version strings.
- Current dependencies include the Compose BOM, activity-compose, Compose
  Material3, Compose UI (+ graphics, tooling, preview), core-ktx,
  lifecycle-runtime-ktx, and test/debug helpers (JUnit, Espresso, Compose UI test).

### AI provider
- Leading choice: **Google Gemini (2.5 Flash)** — multimodal, fast enough for the
  5s NFR, and has a perpetual free tier suitable for a course project. (Qwen and
  Haiku/GPT-4o-mini were considered as alternatives/fallbacks.)
- **The AI API key must never be exposed on the client** (NFR 4.3). Calls should
  be routed/proxied so the key stays server-side.
- Use Kotlin **coroutines** for async AI calls.

---

## 5. Data & Storage Architecture

> This is the team's specific design decision — follow it exactly.

### Cloud-first with required login
- **Most data is stored in the cloud**, so the app **requires a user login**.
  *(The proposal listed accounts as "optional"; the team has since decided login
  is required because of the cloud-first storage model.)*
- Account credentials, saved paths, and progress must be **stored and transmitted
  securely** (NFR 4.1). **Passwords hashed** with industry-standard auth (NFR 4.2).

### The learning tree is driven by a JSON file
The roadmap/tree is represented by a **JSON file** describing the tree structure.
The Compose tree renderer reads this JSON and renders accordingly. **Any change to
the tree = edit the JSON and re-render the new input.**

The JSON holds **only high-level structural info**, e.g.:
- node title
- completed / not-completed flag
- (and other structural/state fields, e.g. ordering, children, node id, est. time)

**Important:** to avoid bloating the JSON, **the actual in-depth content of a node
is NOT stored in the JSON.** Instead, each node holds a **link/reference to a
separate file** where the full content lives. Content is fetched via that link
when the node is opened.

#### Illustrative JSON shape (agent should refine, but keep the principle)
```jsonc
{
  "topicId": "ml-101",
  "title": "Machine learning",
  "mode": "learner",            // "learner" | "tinkerer"
  "nodes": [
    {
      "id": "what-is-ml",
      "title": "What is ML?",
      "completed": true,
      "estMinutes": 5,
      "children": ["types-of-learning"],
      "contentRef": "content/ml-101/what-is-ml.md"  // link to actual content file
    }
    // ...more nodes
  ]
}
```
- Keep node objects **lightweight** (title, flags, ordering, child links, content ref).
- Resolve `contentRef` lazily when the user opens a node.

### Offline behavior (NFR 3.2 + Conflict 2 decision)
- Locally **cache** the text, structure, and progress states of previously saved paths.
- **Offline allowed:** read saved content, update Tinkerer checklists, mark
  subtopics complete.
- **Offline disabled (gracefully):** AI chat, generating new content, expanding
  not-yet-generated nodes — show a meaningful error (NFR 3.1), never crash.

---

## 6. Functional Requirements (authoritative list from proposal v1.0)

### FR1 — Topic & Goal Entry
- **1.1** Let a user enter a topic to learn (Learner) or a task to accomplish (Tinkerer).

### FR2 — Learning Mode
- **2.1** Generate a roadmap that breaks a topic into subtopics.
- **2.2** View the roadmap as a visual tree or a standard list.
- **2.3** Open a subtopic's content when selected.
- **2.4** Mark subtopics complete; show progress as node states (tree) or a progress bar (list).

### FR3 — Tinkering Mode
- **3.1** Generate a flat, ordered step-by-step guide for the task.
- **3.2** Show progress as a step checklist with a linear progress bar.

### FR4 — Content Generation
- **4.1** Generate content in clickable sections/blocks that users can open to chat about (see FR5).
- **4.2** Generate content based on user preferences (difficulty, length, format).
- **4.3** Generate an estimated time to complete for each subtopic/step.
- **4.4** Generate visualizations to accompany the material or guide.
- **4.5** Let a user manually edit any generated content.

### FR5 — AI Chat Assistance
- **5.1** Provide a multi-modal AI chat for help with the current material.
- **5.2** Let a user select/highlight part of the content to ask about it.
- **5.3** Answer the user's questions about the material in chat.
- **5.4** Update content on request or suggest changes the user can accept.
- **5.5** Save and let a user revisit conversation history for all chats.

### FR6 — Quizzes (Learn Mode, Optional)
- **6.1** Optionally generate quizzes in multiple formats (e.g. MCQ, timed).
- **6.2** Let a user select, type, or upload a photo of their work to answer.
- **6.3** Grade answers with feedback and explanations.
- **6.4** Save quizzes and the user's answers.
- **6.5** Let a user retake a quiz.
- **6.6** Let a user compare a retaken quiz's answers with previous attempts.

### FR7 — Managing Topics & Guides
- **7.1** Save a user's topics and guides.
- **7.2** Resume a saved topic or guide.
- **7.3** Remove a topic or guide.
- **7.4** Export a topic or guide for others to use.
- **7.5** Import a topic or guide from others.

### FR8 — Account
- **8.1** Create an account to sync data across devices.
  *(Now treated as required due to cloud-first storage — see §5.)*

---

## 7. Non-Functional Requirements

### Performance
- **1.1** AI-generated learning paths should load within ~5 seconds.
- **1.2** Navigation between subtopics, saved paths, and resource links should feel
  instant — no noticeable lag or UI glitch.

### Usability
- **2.1** A new user should generate their first learning path with no tutorial.
- **2.2** UI must be clean and uncluttered for users of all ages/backgrounds.

### Reliability
- **3.1** Handle failures gracefully — show a meaningful error if AI/network drops,
  never crash.
- **3.2** Previously saved paths must always be accessible, even offline.

### Security
- **4.1** Store and transmit account credentials, saved paths, and progress securely.
- **4.2** Hash passwords with industry-standard authentication.
- **4.3** Never expose AI service API keys on the client.

### Maintainability
- **5.1** Follow a defined GUI architecture → **MVP** (chosen).
- **5.2** Code should be well documented so a new dev understands the structure
  without a walkthrough.

---

## 8. Key Screens (from the mock-up, Figure 1)

1. **Topic input / home** — greeting, "What do you want to learn?", a search/type
   field, and a list of popular topics (each showing a subtopic count).
2. **Learning path** — list view of subtopics with progress ("3 of 8 complete",
   est. time left), each item showing duration and a done/active/upcoming state.
3. **Subtopic detail** — quick summary, "why it matters", a "Dive deeper" set of
   resource links (e.g. Wikipedia, a book, a beginner's guide), and a
   "Mark as complete" button. Step indicator at top (e.g. "Step 4 of 8").
4. **Tree view** (Figure 2) — branching graph of nodes with states:
   - **Completed** (green), **Active path** (blue), **Unexplored** (grey),
     **Branch out** (dashed outline — affordance to expand/add a new branch).

---

## 9. User Scenarios (persona: "Jordan", 1A UW ECE student)

- **Scenario 1 (Learner / tree generation):** Jordan types "cooking for beginners".
  App generates a structured learning tree (Kitchen Basics, Knife Skills, Simple
  Techniques…), each with an estimated time. He switches to tree view to see how
  concepts connect.
- **Scenario 2 (inline AI chat):** Inside "Knife Skills", a passage on dicing onions
  is unclear. He highlights the sentence, opens inline AI chat, asks about it, then
  follows up about safety. He marks the subtopic complete; the progress bar advances.
- **Scenario 3 (Tinkerer / multi-modal):** He switches to Tinkerer mode, types
  "make a breakfast omelette". App generates a step-by-step checklist. Mid-way he
  photographs the pan and asks via chat whether the stove temperature is right; the
  app responds after viewing the photo. He checks off steps as he goes.

---

## 10. Design Considerations

### Human values
- **Autonomy:** users direct their own learning; trees/guides are generated for any
  topic and can be dynamically modified.
- **Inclusivity:** clean uncluttered UI for all ages; multi-modal chat (text + photo)
  supports diverse learning styles.
- **Transparency:** because AI generates the material, users can manually edit content
  and track progress — they remain the authority, guarding against AI errors/hallucinations.


## 11. Milestone Schedule (12 weeks)

| Phase | Week | Work |
|---|---|---|
| Setup & Design | 1 | Project & repo setup |
| | 2 | Design & proposal finalization |
| Core Development | 3 | Core AI integration |
| | 4 | Subtopic generation test & start UI |
| | 5 | Learning tree base |
| | 6 | Learning tree interactions |
| Advanced Features | 7 | Chatbot for every subtopic |
| | 8 | Save, progress & read time |
| | 9 | Export features |
| | 10 | Bring the UI together; ensure it's seamless |
| Testing & Launch | 11 | Testing & bug fixes |
| | 12 | Demo prep |

---

## 12. Guidance for the AI Coding Agent

- Build UI in **Jetpack Compose + Material 3**; no XML layouts.
- Structure code around **MVP**: keep Views (Composables) dumb; put logic in
  **Presenters**; keep data access in **Model/repository** classes. Each screen
  ⇒ a View interface + a Presenter.
- The **learning tree** renders from a **JSON structure** (titles, flags, children,
  ordering, est. time, and a `contentRef` link per node). Full node content lives in
  separate files referenced by `contentRef` — **do not inline content into the tree JSON.**
- **Lazy-load** deep content on node expansion; only the high-level roadmap loads first.
- Treat **login as required** (cloud-first). Hash passwords; transmit/store securely.
- **Never put the AI API key in the client** — route through a backend/proxy.
- Use **coroutines** for AI/network calls; handle failures with graceful, user-facing
  error states (never crash).
- Support **offline reads** of cached saved paths and checklist/completion updates;
  disable dynamic AI features offline gracefully.
- Reference dependencies through the **version catalog** (`libs.*`); respect
  `minSdk 26 / target 37 / compile 37` and Java 11.
- Comment code so a new developer understands the structure without a walkthrough.