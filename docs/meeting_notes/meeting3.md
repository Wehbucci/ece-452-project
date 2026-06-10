June 10

Meeting Purpose

To align on the initial development strategy and assign tasks.

Key Takeaways

  - Architecture: Adopted the simpler MVP (Model-View-Presenter) pattern over MVVM to accelerate development.
  - AI Strategy: Will use free resources (Waterloo's Claude access, Google Cloud trial) and design a swappable AI provider layer to enable easy model switching for testing and demos.
  - Tech Stack: Kotlin on Android 8.0 (API 26) for 98% device compatibility.
  - Task Assignment: Teams are set for Front-end (Hasan, Ali), Back-end (Leo, Andria), and AI Integration (Ady, Richard).

Topics

Development Architecture

  - Decision: Adopt MVP (Model-View-Presenter) over MVVM (Model-View-ViewModel).
  - Rationale: MVP is simpler and faster to implement, avoiding the state management complexity of MVVM.

AI Model & Integration

  - Model Selection:
      - Primary: Claude Haiku (fast, budget-friendly).
      - Backup: Gemini (via free $300 Google Cloud trial).
  - Cost Management:
      - Primary: Leverage free Waterloo student access to Claude.
      - Backup: Split costs for any paid API usage.
  - Integration Strategy:
      - Design: Build a swappable AI provider layer.
      - Rationale: Enables easy model switching for testing, performance comparison, and using the best model for the final demo.

Tech Stack & Core Logic

  - Language: Kotlin (Google's recommended standard).
  - SDK: Android 8.0 (API 26) → Ensures 98% device compatibility.
  - Tree Rendering:
      - Structure: A single JSON file will define the tree's hierarchy and node relationships.
      - Content: Node content (text, media) will be stored separately to prevent the JSON file from becoming too large.

Data Storage & Backend

  - Requirement: User accounts are mandatory for cloud storage, as recommended by the TA.
  - Plan: Store all user data (including node content) in a cloud database, not locally on-device.

Task Assignment & Deadlines

  - Team Assignments:
      - Front-end: Hasan, Ali
      - Back-end: Leo, Andria
      - AI Integration: Ady, Richard
  - Deadlines:
      - Front-end (Hasan, Ali): Sunday, June 14.
          - Deliverable: Core app structure, MVP framework, and a few blank pages for navigation.
          - Rationale: Provides a foundation for other teams to begin integration.
      - All Teams: Wednesday, June 17.
          - Deliverable: Initial progress on assigned tasks (e.g., backend design, AI testing).

Next Steps

  - Hasan:
      - Set up the project repository with the defined tech stack (Kotlin, Android 8.0).
      - Share the repo link with the team.
  - Hasan & Ali (Front-end):
      - By Sunday, June 14: Deliver the core app structure, MVP framework, and initial blank pages.
  - Leo & Andria (Back-end):
      - Begin designing the backend architecture and database schema.
  - Ady & Richard (AI Integration):
      - Research and test AI models, focusing on leveraging free resources.
      - Prepare to integrate a test chatbot once the front-end foundation is ready.
  - All Team Members:
      - Research and prepare for assigned tasks while awaiting the repo setup.

### Duration: 60 min 
