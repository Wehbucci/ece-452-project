July 15
Meeting Purpose

Sync on project progress and define the path to the final demo.

Key Takeaways

  - Backend Adopted: Andrea's backend (tree generation, Firestore) is the project standard, as it's the most complete implementation.
  - UI Ready for Merge: The new gamified UI is ready for integration after minor fixes (text overlap, branch reset bug).
  - Merge Plan: Frontend merges first, followed by the backend. The team will then link them, targeting functional tree generation by next Wednesday.
  - Demo Deadline: The final presentation is August 4th or 5th, providing a clear target for completion.

Topics

Backend & AI Progress

  - Andrea's backend implementation is the most complete and will be adopted as the standard.
  - Functionality:
      - Generates learning trees (paths) in Firestore.
      - Limits tree complexity (e.g., 5-8 nodes for simple topics, max 15 for complex ones) to ensure manageable roadmaps.
      - Supports difficulty settings (e.g., beginner) via a prompt, which will connect to the frontend's "Learning Preferences" section.
  - Next Steps:
      - Andrea will push the code and share the tree-generation prompt.
      - Richard will integrate his content generation work into Andrea's branch.

Frontend UI Progress

  - The new gamified UI, matching the concept HTML, is complete.
  - Known Issues:
      - Text labels overlap the learning path.
      - Branch-creation logic is flawed, preventing multi-track paths.
      - Chat history resets on specific branches.
  - Decision: The UI's single-path structure is sufficient for the demo; no multi-track branching will be implemented.
  - Suggestion: Remove the "Popular Topics" section, as it adds unnecessary complexity.

Project Management & Timeline

  - Demo Deadline: August 4th or 5th.
  - Merge Strategy:
    1.  Frontend First: Merge the UI branch to establish the visual foundation.
    2.  Backend Second: Merge Andrea's backend branch.
    3.  Link & Integrate: Connect the frontend to the backend.
  - Next Milestone (by next Wednesday): Achieve functional tree generation from the UI.

Next Steps

  - Andrea:
      - Push backend code to the main branch.
      - Share the tree-generation prompt.
  - Hasan:
      - Create a PR for the new UI branch by tomorrow.
  - Richard:
      - Integrate content generation into Andrea's backend branch.
  - Team:
      - Fix known UI bugs (text overlap, branch reset).
      - Link the merged frontend and backend.
      - Achieve functional tree generation by next Wednesday.

### Duration: 30 min