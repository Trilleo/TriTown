package net.trilleo.mc.plugins.tritown.enums

/**
 * How a paged GUI arranges its content area.
 *
 * The navigation row is reserved either way; this only decides what the rest of
 * the inventory does with the space above it.
 */
enum class PagedLayout {

    /** Every slot above the navigation row holds content. The most room, and no border. */
    FULL,

    /**
     * Content sits in an inset block with a one-slot border around it.
     *
     * Costs the outer ring of slots — a six-row menu holds 28 items a page
     * rather than 45 — and buys a menu that reads as a thing rather than as a
     * grid of loose items.
     */
    FRAMED,

    /**
     * Framed, with each page's items centred inside the border rather than
     * filled from its top-left corner.
     *
     * A full page looks exactly like [FRAMED]. A page holding a few — the last
     * one, or a list that is short to begin with — keeps its items together in
     * the middle of the menu, with the last row packed around the centre column.
     * Meant for lists that are often short, such as the players online.
     */
    CENTERED,
}
