package com.classycode.windowwatcher

/**
 * Encapsulation of UI state
 */
data class UiState(
    val connected: Boolean,
    val statusMessage: String,
    val window1Open: Boolean?,
    val window2Open: Boolean?,
    val window3Open: Boolean?,
    val window4Open: Boolean?,
    val messageCount: Int
) {

    val incompleteSensorData
        get() =
            window1Open == null || window2Open == null || window3Open == null || window4Open == null

    val anyWindowIsOpen
        get() =
            window1Open == true || window2Open == true || window3Open == true || window4Open == true
}
