package io.github.sds100.keymapper.inputmethod.keyboard.clipboard

interface OnKeyEventListener {

    fun onKeyDown(clipId: Long)

    fun onKeyUp(clipId: Long)

}