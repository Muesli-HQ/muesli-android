package com.phequals7.muesli.meetings

/**
 * Meeting note templates, ported from muesli-ios MeetingTemplatePreset
 * (MuesliPreferences.swift). Labels and details are verbatim; the summary
 * instruction prompts land together with the LLM summaries feature.
 */
enum class MeetingTemplate(
    val id: String,
    val label: String,
    val detail: String,
) {
    GENERAL("general", "General Meeting", "Balanced notes, decisions, and action items."),
    ONE_ON_ONE("oneOnOne", "1:1", "Feedback, blockers, follow-ups, and commitments."),
    STANDUP("standup", "Standup", "Progress, next work, blockers, and owners."),
    INTERVIEW("interview", "Interview", "Signals, strengths, concerns, and follow-up questions."),
    LECTURE("lecture", "Lecture", "Concepts, examples, questions, and study follow-ups."),
    CUSTOMER_CALL("customerCall", "Customer Call", "Pain points, requirements, objections, and next steps."),
    PLANNING("planning", "Planning", "Goals, scope, risks, milestones, and open questions.");

    companion object {
        fun fromId(id: String?): MeetingTemplate = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}
