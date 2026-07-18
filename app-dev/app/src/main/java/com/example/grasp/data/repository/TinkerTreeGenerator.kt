// TODO: Complete implementation of Tinker generation

package com.example.grasp.data.repository

import com.example.grasp.data.model.TreeNode

fun buildTinkerTree(
    pathId: String,
    title: String
): List<TreeNode> {

    val guide = FakePathRepository.tinkerGuide(pathId) ?: return emptyList()

    return guide.steps.mapIndexed { index, step ->
        TreeNode(
            id = step.id,
            title = step.instruction,
            completed = step.done,
            estMinutes = step.estMinutes,
            children = if (index < guide.steps.size - 1) {
                listOf(guide.steps[index + 1].id)
            } else {
                emptyList()
            },
            contentRef = "content/$pathId/${step.id}.md"
        )
    }
}