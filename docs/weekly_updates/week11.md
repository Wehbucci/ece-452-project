# Project Null Pointers Week 11 Update

July 13 - July 20

- [TEAM'S RESPONSE TO LAST WEEK'S FEEDBACK] No roadblocks — during that period the team had moved to feature-branch development (`gamefy-roadmap`, `backend-setup`) instead of committing directly to main, so commits were still happening, just not visible on main until both branches merged this week.
- [Hasan, Ali] fixed the UI bugs flagged in meeting 7 (overlapping text labels, the branch-reset bug) and merged the gamified roadmap into main via PR #1, making it the app's new visual foundation.
- [Andria] merged the backend-setup branch into main via PR #2: trees are now generated with Gemini instead of hardcoded example topics, topics/nodes are saved to and loaded from Firestore with parent/child tracking, topic deletion syncs to the backend, and the tree-layout bug that rendered trees as a straight line was fixed.
- [Andria] still has to implement Tinkerer mode, topic branching, and persistence of node completion/progress to the db, and needs to replace the hardcoded 0 for estimated time per node with a real generated value; there's also a lag when generating/loading topics that may need a loading state.
- [This week] the frontend and backend are both merged to main per the meeting 7 plan; next step is linking them to achieve functional, database-driven tree generation from the UI ahead of the August 4/5 demo.
