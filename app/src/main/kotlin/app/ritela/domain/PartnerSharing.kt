package app.ritela.domain

/** Explicit selections retain the existing wire format without DIARY's blanket grant. */
object PartnerSharing {
    /** Adds only the explicitly confirmed selection, preserving unrelated existing grants. */
    fun union(existing: ShareScope, selected: ShareScope): ShareScope = ShareScope(
        existing.categories + selected.categories,
        existing.sections + selected.sections,
        (existing.tags.keys + selected.tags.keys).associateWith {
            existing.tags[it].orEmpty() + selected.tags[it].orEmpty()
        }
    ).normalized()

    fun defaultDiary(scope: ShareScope, allowed: ShareScope): ShareScope {
        val basic = setOf("mood", "pain", "symptoms", "energy", "discharge")
        return diary(
            scope,
            allowed.sections.intersect(basic),
            allowed.tags.filterKeys {
                it in basic
            }
        )
    }
    fun explicit(scope: ShareScope, layout: JournalLayout): ShareScope {
        if (ShareCategory.DIARY !in scope.categories) return scope.normalized()
        return scope.copy(
            categories =
                scope.categories - ShareCategory.DIARY + ShareCategory.SECTIONS,
            sections = scope.sections + layout.historySections().map {
                it.id
            } + "note"
        ).normalized()
    }

    fun section(scope: ShareScope, id: String, checked: Boolean): ShareScope {
        val sections = if (checked) scope.sections + id else scope.sections - id
        val tags = scope.tags - id
        return diary(scope, sections, tags)
    }

    fun tag(scope: ShareScope, section: JournalSection, id: String, checked: Boolean): ShareScope {
        val previous = if (section.id in scope.sections) {
            section.tags.map { it.id }.toSet()
        } else {
            scope.tags[section.id].orEmpty()
        }
        val values = if (checked) previous + id else previous - id
        return diary(scope, scope.sections - section.id, scope.tags + (section.id to values))
    }

    fun diary(
        scope: ShareScope,
        sections: Set<String>,
        tags: Map<String, Set<String>>
    ): ShareScope {
        val nonempty = tags.filterValues { it.isNotEmpty() }.filterKeys { it !in sections }
        var categories =
            scope.categories -
                setOf(ShareCategory.DIARY, ShareCategory.SECTIONS, ShareCategory.TAGS)
        if (sections.isNotEmpty()) categories = categories + ShareCategory.SECTIONS
        if (nonempty.isNotEmpty()) categories = categories + ShareCategory.TAGS
        return ShareScope(categories, sections, nonempty)
    }
}
