package com.nuvio.app.features.extras

internal actual object PartyInviteNotifier {
    // No system notification on desktop: returning false lets the app show the invite itself.
    actual suspend fun show(invite: PartyInvite): Boolean = false
}
