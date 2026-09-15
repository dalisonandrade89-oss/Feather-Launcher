# Mantemos regras enxutas de propósito: quanto mais código morto o R8
# remover, menor o número de classes carregadas -> menos RSS em runtime.

-keepattributes *Annotation*
-dontwarn kotlinx.coroutines.**

# DataStore usa reflection mínima em alguns pontos internos
-keep class androidx.datastore.*.** { *; }
