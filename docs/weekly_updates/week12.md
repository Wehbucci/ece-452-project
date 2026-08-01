# Project Null Pointers Week 12 Update

July 20 - July 27

- [All] adopted the direct-to-main, with each person committing straight into their feature area throughout the week instead of batching everything into one merge.
- [Hasan] gamified and redesigned the entire UI on top of the new bevelled game-card kit: rebuilt the bottom bar with an animated selection pill, restyled login, home (with a "jump back in" banner), the library (mode filters, delete confirmation, read off the main thread), and the tinker guide, and added real level/XP stats tracking plus notification preferences and an about screen to the profile tab.
- [Richard] implemented the tree branching mechanism (pick a node to grow a branch from, or drop a new section anywhere on the roadmap) and node content generation (full lessons with headings, worked examples, diagrams and sourced images, generated up front behind a loading screen), and overhauled the roadmap board itself with pinch-to-zoom/pan, a centred tree layout, and always opening on the learner's current node.
- [Andria] implemented Tinker Mode end-to-end: AI-generated tinker guides, progress persisted to Firestore, guides listed in the library, and deletion support, with the generation prompt tuned afterward.
- [Leo] merged login and node-completion progress persistence.
- [This week] with the gamified UI, branching, content generation, and Tinker Mode all merged to main, the project is roughly 90% complete; the team is shifting focus to polish, performance/optimization, and testing ahead of the August 4/5 demo.
