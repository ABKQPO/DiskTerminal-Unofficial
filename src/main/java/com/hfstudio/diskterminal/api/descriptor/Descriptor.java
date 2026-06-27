package com.hfstudio.diskterminal.api.descriptor;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.hfstudio.diskterminal.api.identity.TargetId;

/**
 * Static, immutable description of an object: <em>what it is</em>, never <em>what it can do</em>.
 * <p>
 * A descriptor only carries stable presentation and location data used for caching, network sync, UI
 * selection and action targeting. It must never hold dynamic behavior state (priority, custom name,
 * filters, contents, upgrades) or a real runtime object reference.
 * <p>
 * The dimension is modelled as a primitive id because 1.7.10 has no {@code ResourceKey} world type.
 *
 * @param <ID> the identity type of the described object
 */
public interface Descriptor<ID extends TargetId> {

    /**
     * Stable identity of the described object.
     */
    ID getId();

    /**
     * Block position of the described object.
     */
    BlockPos getPosition();

    /**
     * Dimension id the described object lives in.
     */
    int getDimension();

    /**
     * Icon used to present the described object.
     */
    ItemStack getIcon();
}
