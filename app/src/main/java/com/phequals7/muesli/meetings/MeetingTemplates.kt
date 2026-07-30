package com.phequals7.muesli.meetings

/**
 * Meeting note templates, ported from muesli-ios MeetingTemplatePreset
 * (MuesliPreferences.swift). Labels, details, and the full summary-prompt
 * instruction blocks are verbatim — keep in sync.
 */
enum class MeetingTemplate(
    val id: String,
    val label: String,
    val detail: String,
    val instructions: String,
) {
    GENERAL(
        "general", "General Meeting", "Balanced notes, decisions, and action items.",
        """Follow this note template exactly:

## Meeting Summary
A 2-3 sentence overview of what was discussed.

## Key Discussion Points
- Bullet points of the main topics discussed

## Decisions Made
- Bullet points of any decisions reached

## Action Items
- [ ] Bullet points of tasks assigned or agreed upon, with owners if mentioned

## Notable Quotes
- Any important or notable statements, if applicable"""
    ),
    ONE_ON_ONE(
        "oneOnOne", "1:1", "Feedback, blockers, follow-ups, and commitments.",
        """Follow this note template exactly:

## 1:1 Summary
A concise overview of the conversation and current context.

## Wins and Progress
- Bullet points of progress, positive signals, and completed work

## Blockers and Concerns
- Bullet points of blockers, risks, or concerns discussed

## Feedback
- Bullet points of feedback given or requested

## Action Items
- [ ] Follow-up tasks, owners, and timing if mentioned"""
    ),
    STANDUP(
        "standup", "Standup", "Progress, next work, blockers, and owners.",
        """Follow this note template exactly:

## Standup Summary
A concise overview of team status.

## Progress
- What was completed or moved forward

## Next Up
- What people plan to work on next

## Blockers
- Blockers, dependencies, or risks

## Action Items
- [ ] Follow-up tasks with owners if mentioned"""
    ),
    INTERVIEW(
        "interview", "Interview", "Signals, strengths, concerns, and follow-up questions.",
        """Follow this note template exactly:

## Interview Summary
A concise overview of the interview discussion.

## Key Signals
- Evidence, examples, and signals observed

## Strengths
- Strengths or positive indicators

## Concerns
- Concerns, gaps, or unclear areas

## Follow-up Questions
- Questions or topics to revisit"""
    ),
    LECTURE(
        "lecture", "Lecture", "Concepts, examples, questions, and study follow-ups.",
        """Follow this note template exactly:

## Lecture Summary
A concise overview of the session.

## Core Concepts
- Main concepts and definitions

## Examples and Evidence
- Examples, cases, formulas, or references mentioned

## Questions
- Questions raised or unclear points

## Follow-ups
- [ ] Study tasks, readings, or practice items"""
    ),
    CUSTOMER_CALL(
        "customerCall", "Customer Call", "Pain points, requirements, objections, and next steps.",
        """Follow this note template exactly:

## Customer Call Summary
A concise overview of the customer conversation.

## Customer Goals
- Desired outcomes, priorities, and success criteria

## Pain Points
- Problems, blockers, or frustrations mentioned

## Requirements
- Feature, workflow, technical, or commercial requirements

## Objections and Risks
- Concerns, objections, or adoption risks

## Next Steps
- [ ] Follow-up tasks, owners, and timing if mentioned"""
    ),
    PLANNING(
        "planning", "Planning", "Goals, scope, risks, milestones, and open questions.",
        """Follow this note template exactly:

## Planning Summary
A concise overview of the plan discussed.

## Goals
- Outcomes and priorities

## Scope
- In-scope work, out-of-scope work, and dependencies

## Risks and Open Questions
- Risks, unknowns, and decisions still needed

## Milestones
- Dates, phases, or checkpoints if mentioned

## Action Items
- [ ] Follow-up tasks with owners if mentioned"""
    );

    companion object {
        fun fromId(id: String?): MeetingTemplate = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}
