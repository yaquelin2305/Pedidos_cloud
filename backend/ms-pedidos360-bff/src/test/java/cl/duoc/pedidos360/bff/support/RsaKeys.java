package cl.duoc.pedidos360.bff.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Par de claves RSA generado en memoria para firmar tokens de prueba y validarlos
 * sin depender de un JWK endpoint real.
 */
public final class RsaKeys {

    public final RSAPublicKey publicKey;
    public final RSAPrivateKey privateKey;

    public RsaKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA de prueba", e);
        }
    }
}
