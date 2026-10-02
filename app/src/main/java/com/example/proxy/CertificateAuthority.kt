package com.example.proxy

import android.content.Context
import android.security.KeyChain
import android.content.Intent
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal

object CertificateAuthority {
    private const val STORE_FILE = "devtraffic-ca.p12"
    private const val STORE_PASSWORD = "DevTrafficLocalCA-2026"

    init {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
    }

    private data class Root(val key: PrivateKey, val cert: X509Certificate)

    @Synchronized
    private fun root(context: Context): Root {
        val file = File(context.filesDir, STORE_FILE)
        if (file.exists()) {
            val ks = KeyStore.getInstance("PKCS12")
            FileInputStream(file).use { ks.load(it, STORE_PASSWORD.toCharArray()) }
            val key = ks.getKey("root", STORE_PASSWORD.toCharArray()) as PrivateKey
            val cert = ks.getCertificate("root") as X509Certificate
            return Root(key, cert)
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val pair = generator.generateKeyPair()
        val now = Date()
        val until = Date(now.time + 3650L * 24 * 60 * 60 * 1000)
        val subject = X500Name("CN=DevTraffic Inspector Local CA,O=DevTraffic Inspector")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            now,
            until,
            subject,
            pair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature)
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(pair.private)
        val cert = JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, STORE_PASSWORD.toCharArray())
        ks.setKeyEntry("root", pair.private, STORE_PASSWORD.toCharArray(), arrayOf(cert))
        FileOutputStream(file).use { ks.store(it, STORE_PASSWORD.toCharArray()) }
        return Root(pair.private, cert)
    }

    fun installIntent(context: Context): Intent {
        val r = root(context)
        return KeyChain.createInstallIntent().apply {
            putExtra(KeyChain.EXTRA_CERTIFICATE, r.cert.encoded)
            putExtra(KeyChain.EXTRA_NAME, "DevTraffic Inspector Local CA")
        }
    }

    fun isCreated(context: Context): Boolean =
        File(context.filesDir, STORE_FILE).exists()

    fun createServerContext(context: Context, host: String): SSLContext {
        val r = root(context)
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val leafPair: KeyPair = generator.generateKeyPair()
        val now = Date()
        val until = Date(now.time + 7L * 24 * 60 * 60 * 1000)
        val issuer = X500Name(r.cert.subjectX500Principal.name)
        val subject = X500Name("CN=$host")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            BigInteger(160, SecureRandom()),
            now,
            until,
            subject,
            leafPair.public
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(GeneralName.dNSName, host))
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(r.key)
        val leaf = JcaX509CertificateConverter().setProvider("BC")
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, STORE_PASSWORD.toCharArray())
        ks.setKeyEntry(
            "leaf",
            leafPair.private,
            STORE_PASSWORD.toCharArray(),
            arrayOf(leaf, r.cert)
        )
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        km.init(ks, STORE_PASSWORD.toCharArray())

        return SSLContext.getInstance("TLS").apply {
            init(km.keyManagers, null, SecureRandom())
        }
    }
}
