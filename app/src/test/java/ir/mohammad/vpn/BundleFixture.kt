package ir.mohammad.vpn

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Builds a bundle in the exact on-disk format the app reads, so the loader's success path can be
 * tested without the operator's real bundle (which is git-ignored and must never be committed).
 *
 * Format: "MHB1" | salt[16] | iv[12] | AES-256-GCM(ciphertext || tag)
 * Key:    PBKDF2-HMAC-SHA256(password, salt, 210000, 32 bytes)
 *
 * The password below exists only in test sources. It is not, and must never become, an operator
 * password; a test asserts that it does not decrypt the shipped asset when one is present.
 */
object BundleFixture {

    const val PASSWORD = "test-only-fixture-password-8f3a"

    private const val MAGIC = "MHB1"
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    /** Minimal bundle that satisfies every validation rule in [ir.mohammad.vpn.data.OperatorBundleLoader]. */
    val json: String = """
        {
          "schemaVersion": 1,
          "defaultProfileId": "fixture-primary",
          "defaultDnsServers": ["1.1.1.1", "8.8.8.8"],
          "profiles": [
            {
              "id": "fixture-primary",
              "name": "Fixture",
              "protocol": "VLESS",
              "core": "XRAY",
              "serverHost": "fixture.invalid",
              "serverPort": 443,
              "xray": {
                "protocol": "vless",
                "settings": {}
              }
            }
          ]
        }
    """.trimIndent()

    fun encrypted(password: String = PASSWORD, body: String = json): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(body.toByteArray(Charsets.UTF_8))

        return MAGIC.toByteArray(Charsets.US_ASCII) + salt + iv + cipherText
    }
}
