package com.example.aigamerfriend.model

enum class Emotion {
    NEUTRAL,
    HAPPY,
    EXCITED,
    SURPRISED,
    THINKING,
    WORRIED,
    SAD,
    ;

    companion object {
        fun fromString(value: String): Emotion =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: NEUTRAL
    }
}
