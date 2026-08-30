# Goose restores persisted back stacks by resolving each screen's serialized JVM class name
# reflectively (Class.forName + its kotlinx serializer). Keep original names for Screen
# implementations so restoration works across minified releases. Apps that register every
# screen explicitly via screenSerializers { } may remove this in their own config.
-keepnames class * implements dev.goose.runtime.Screen
