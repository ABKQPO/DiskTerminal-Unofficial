package com.hfstudio.diskterminal.api.snapshot;

import com.hfstudio.diskterminal.api.identity.TargetId;

/**
 * Dynamic read model produced on the server and sent to the client: <em>what should currently be
 * displayed</em>. A snapshot is used for rendering, searching, sorting and state display only. It must
 * never become a behavior entry point, hold a real runtime reference, or resolve capabilities.
 *
 * @param <ID> the identity type of the snapshotted object
 */
public interface Snapshot<ID extends TargetId> {

    /**
     * Stable identity of the snapshotted object.
     */
    ID getId();
}
