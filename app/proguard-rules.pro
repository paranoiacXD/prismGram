# dont touch this, the tdlib classes are called from jni by name
-keep class org.drinkless.tdlib.** { *; }

# tdlib reaches into its api classes from native code
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-dontwarn org.drinkless.tdlib.**
