package io.github.sds100.keymapper;

import android.view.KeyEvent;

interface IKeyEventRelayServiceCallback {
    boolean onKeyEvent(in KeyEvent event, in String sourcePackageName);
}