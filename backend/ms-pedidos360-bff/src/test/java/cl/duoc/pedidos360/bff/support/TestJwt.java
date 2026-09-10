package cl.duoc.pedidos360.bff.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Construye y firma JWT de prueba con claims controlados para ejercitar cada rama de la
 * validacion del BFF: token valido, expirado, con audience o issuer incorrectos.
 */
public final class TestJwt {

    private final RsaKeys keys;
    private final String issuer;
    private final String audience;

    public TestJwt(RsaKeys keys, String issuer, String audience) {
        this.keys = keys;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String valid(List<String> roles) {
        return build(issuer, audience, Instant.now().plus(1, ChronoUnit.HOURS), roles);
    }

    public String expired(List<String> roles) {
        return build(issuer, audience, Instant.now().minus(5, ChronoUnit.MINUTES), roles);
    }

    public String wrongAudience(List<String> roles) {
        return build(issuer, "api://otra-api", Instant.now().plus(1, ChronoUnit.HOURS), roles);
    }

    public String wrongIssuer(List<String> roles) {
        return build("https://issuer-falso.pedidos360.local", audience,
                Instant.now().plus(1, ChronoUnit.HOURS), roles);
    }

    private String build(String iss, String aud, Instant expiry, List<String> roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("user-123")
                    .issuer(iss)
                    .audience(aud)
                    .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                    .expirationTime(Date.from(expiry))
                    .claim("name", "Usuario Prueba")
                    .claim("roles", roles)
                    .claim("scp", "orders.read orders.write")
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                    claims);
            jwt.sign(new RSASSASigner(keys.privateKey));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de prueba", e);
        }
    }
}
