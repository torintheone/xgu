# R8 rules for the release build.

# kotlinx.serialization: saved genomes and dishes (sim module). The code calls the generated serializers
# directly, and the library ships its own rules; these keep the generated parts anyway, so a shrinker change
# can never break opening a save on the phone.
-keepclassmembers class io.github.torintheone.petri.sim.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.torintheone.petri.sim.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.torintheone.petri.sim.**$$serializer { *; }
