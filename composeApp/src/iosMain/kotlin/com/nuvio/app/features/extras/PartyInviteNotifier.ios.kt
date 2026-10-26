package com.nuvio.app.features.extras

internal actual object PartyInviteNotifier {
    // iOS shows invites inside the app only.
    actual suspend fun show(invite: PartyInvite): Boolean = false
}
