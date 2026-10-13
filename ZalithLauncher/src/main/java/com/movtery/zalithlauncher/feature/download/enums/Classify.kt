package com.movtery.zalithlauncher.feature.download.enums

enum class Classify(val type: Int) {
    ALL(-1),
    MOD(0),
    MODPACK(1),
    RESOURCE_PACK(2),
    // Kept for the legacy world import class; it is no longer a download tab.
    WORLD(-1),
    SHADER_PACK(3)
}