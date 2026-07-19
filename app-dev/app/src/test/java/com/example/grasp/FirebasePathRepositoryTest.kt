package com.example.grasp

import com.example.grasp.data.model.Mode
import com.example.grasp.data.repository.buildLearnerTree
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebasePathRepositoryTest {
    @Test
    fun machineLearningTopicBuildsRelevantBranchingNodes() {
        val nodes = buildLearnerTree(
            pathId = "machine-learning",
            title = "Machine Learning",
            mode = Mode.LEARNER,
        )

        val overview = nodes.firstOrNull { it.id == "what-is-machine-learning" }
        val typesOfLearning = nodes.firstOrNull { it.id == "types-of-learning" }
        val dataBasics = nodes.firstOrNull { it.id == "data-basics" }
        val supervised = nodes.firstOrNull { it.id == "supervised-learning" }
        val unsupervised = nodes.firstOrNull { it.id == "unsupervised-learning" }

        assertTrue(overview != null)
        assertTrue(typesOfLearning != null)
        assertTrue(dataBasics != null)
        assertTrue(supervised != null)
        assertTrue(unsupervised != null)
        assertTrue(overview?.children?.contains("types-of-learning") == true)
        assertTrue(typesOfLearning?.children?.contains("supervised-learning") == true)
        assertTrue(typesOfLearning?.children?.contains("unsupervised-learning") == true)
        assertTrue(dataBasics?.children?.contains("model-training") == true)
    }
}
