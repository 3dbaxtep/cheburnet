# Compose/lifecycle: R8 вырезает провайдер LocalLifecycleOwner,
# из-за чего collectAsStateWithLifecycle падает на старте.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
