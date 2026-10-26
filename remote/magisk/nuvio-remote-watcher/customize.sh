# Magisk install hook.
SKIPUNZIP=0
ui_print "- Nuvio Remote Watcher"
ui_print "  Enable 'TV remote' in Nuvio and select your TV."
ui_print "  The watcher starts after a reboot."
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/watcher.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
