# Règles R8 spécifiques à l'application.
# kotlinx.serialization embarque ses propres règles ; on conserve en plus le modèle des données du jeu,
# lu depuis le fichier JSON embarqué.
-keep class io.github.slaynaw.carnetduraider.core.** { *; }

# Messages d'erreur plus lisibles dans les rapports de plantage.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Pas d'obfuscation : pour une application personnelle, des rapports de plantage lisibles valent plus
# qu'un APK légèrement plus petit.
-dontobfuscate
