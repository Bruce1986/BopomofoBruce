# Consumer ProGuard rules for the :decoder module.
#
# bpmf_jni.c 以 Java_com_bopomofobruce_decoder_JniBpmfNative_* 名稱綁定，
# 類別名與 native 方法名都不能被混淆。
-keepclasseswithmembernames class com.bopomofobruce.decoder.JniBpmfNative {
    native <methods>;
}
