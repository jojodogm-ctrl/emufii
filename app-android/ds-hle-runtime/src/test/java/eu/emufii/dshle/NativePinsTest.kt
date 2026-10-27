package eu.emufii.dshle

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativePinsTest {
    @Test fun embeddedRuntimeUsesTheRebuiltSdk500NativeLibraries() {
        val expected = mapOf(
            "libmelonjni.so" to "9bd1b92d70e3a7ca39d99d5074ac51847f8ff2852b70ab82d61c4642ba83b5c9",
            "libc++_shared.so" to "83269b3124a287fac36b17c9fb64beb398c83dad6677b19ce63cf80d09966fd7"
        )
        expected.forEach { (name, pin) ->
            val digest = MessageDigest.getInstance("SHA-256")
            File("src/main/jniLibs/arm64-v8a/$name").inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            assertEquals(name, pin, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        }
        assertFalse(File("src/main/jniLibs/arm64-v8a/libmpdsprobe.so").exists())
        assertFalse(File("src/main/assets/boards").exists())
    }
}
