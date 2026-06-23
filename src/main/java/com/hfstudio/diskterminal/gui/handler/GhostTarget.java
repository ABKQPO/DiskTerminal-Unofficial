package com.hfstudio.diskterminal.gui.handler;

import java.awt.Rectangle;

/**
 * Local stand-in for JEI's {@code IGhostIngredientHandler.Target}.
 * <p>
 * The 1.12 source integrated drag-to-partition through JEI's ghost-ingredient API. On 1.7.10 the
 * equivalent is NEI; until the NEI bridge is wired (Phase 5), tabs expose their partition drop
 * zones through this neutral interface so the porting stays mechanical and provider-agnostic.
 *
 * @param <T> the dragged ingredient type
 */
public interface GhostTarget<T> {

    /**
     * Screen-space rectangle that accepts a dropped ingredient.
     */
    Rectangle getArea();

    /**
     * Apply a dropped ingredient to this target.
     */
    void accept(T ingredient);
}
