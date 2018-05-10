// Copyright 2018 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.things.iotcore;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import org.jose4j.lang.JoseException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

/** JwtGenerator unit tests. */
@RunWith(MockitoJUnitRunner.class)
public class JwtGeneratorTest {

    private static final String JWT_AUDIENCE = "foo";
    private static final KeyPair RSA_KEY_PAIR = generateKeyPair("RSA");
    private static final KeyPair EC_KEY_PAIR = generateKeyPair("EC");
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

    @Mock
    private PrivateKey mMockPrivateKey;
    @Mock
    private PublicKey mMockPublicKey;

    // Generate key pairs for testing
    private static KeyPair generateKeyPair(String algorithm) {
        try {
            if (algorithm.equals("EC")) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
                generator.initialize(256);
                return generator.generateKeyPair();
            }

            KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            fail("Error generating test keypair");
        }
        return null;  // Satisfy compiler
    }

    @Test
    public void testConstructorRsaKeyAlgorithm() {
        KeyPair kp = new KeyPair(mMockPublicKey, mMockPrivateKey);
        when(mMockPrivateKey.getAlgorithm()).thenReturn("RSA");
        when(mMockPrivateKey.getAlgorithm()).thenReturn("RSA");

        assertThat(new JwtGenerator(kp, JWT_AUDIENCE, TOKEN_LIFETIME)).isNotNull();
        int numPrivateGetAlgorithmCalled = Mockito.mockingDetails(mMockPrivateKey)
                .getInvocations()
                .size();
        int numPublicGetAlgorithmCalled = Mockito.mockingDetails(mMockPublicKey)
                .getInvocations()
                .size();
        assertThat(numPrivateGetAlgorithmCalled + numPublicGetAlgorithmCalled).isEqualTo(1);
    }

    @Test
    public void testConstructorEcKeyAlgorithm() {
        KeyPair kp = new KeyPair(mMockPublicKey, mMockPrivateKey);
        when(mMockPrivateKey.getAlgorithm()).thenReturn("EC");
        when(mMockPrivateKey.getAlgorithm()).thenReturn("EC");

        assertThat(new JwtGenerator(kp, JWT_AUDIENCE, TOKEN_LIFETIME)).isNotNull();
        int numPrivateGetAlgorithmCalled = Mockito.mockingDetails(mMockPrivateKey)
                .getInvocations()
                .size();
        int numPublicGetAlgorithmCalled = Mockito.mockingDetails(mMockPublicKey)
                .getInvocations()
                .size();
        assertThat(numPrivateGetAlgorithmCalled + numPublicGetAlgorithmCalled).isEqualTo(1);
    }

    @Test
    public void testConstructorInvalidKeyAlgorithm() {
        KeyPair kp = new KeyPair(mMockPublicKey, mMockPrivateKey);
        when(mMockPrivateKey.getAlgorithm()).thenReturn("bad");
        when(mMockPrivateKey.getAlgorithm()).thenReturn("bad");

        try {
            new JwtGenerator(kp, JWT_AUDIENCE, TOKEN_LIFETIME);
            fail("JwtGenerator constructed with unsupported encryption algorithm");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("unsupported");

            int numPrivateGetAlgorithmCalled = Mockito.mockingDetails(mMockPrivateKey)
                    .getInvocations()
                    .size();
            int numPublicGetAlgorithmCalled = Mockito.mockingDetails(mMockPublicKey)
                    .getInvocations()
                    .size();
            assertThat(numPrivateGetAlgorithmCalled + numPublicGetAlgorithmCalled).isEqualTo(1);
        }
    }

    /**
     * Make sure Jwt created is formatted according to the Google Cloud IoT Core<a
     * href="https://cloud.google.com/iot/docs/how-tos/credentials/jwts#jwt_composition">spec</a>.
     */
    @Test
    public void testCreateJwtRsa() throws JoseException {
        JwtGenerator jwtGenerator =
                new JwtGenerator(RSA_KEY_PAIR, JWT_AUDIENCE, TOKEN_LIFETIME, TEST_CLOCK);
        String rawJwt = jwtGenerator.createJwt();

        // Validate JWT
        Jws<Claims> parsedJwt = Jwts.parser()
                .setSigningKey(RSA_KEY_PAIR.getPublic())
                .parseClaimsJws(rawJwt);

        JwsHeader header = parsedJwt.getHeader();
        Claims claims = parsedJwt.getBody();

        assertThat(header.getAlgorithm()).isEqualTo("RS256");
        assertThat(header.getType()).isEqualTo("JWT");
        assertThat(claims.getAudience()).isEqualTo(JWT_AUDIENCE);

        // JWT requires time in seconds from epoch, not millis, so allow issue time within one
        // second.
        assertThat(claims.getIssuedAt().getTime()).isAtLeast(TEST_CLOCK.millis() - 1000);
        assertThat(claims.getIssuedAt().getTime()).isAtMost(TEST_CLOCK.millis() + 1000);

        // Check expiration time within one second of issue time + TOKEN_LIFETIME
        assertThat(claims.getExpiration().getTime())
                .isLessThan(Clock.offset(TEST_CLOCK, TOKEN_LIFETIME.plusSeconds(1)).millis());
        assertThat(claims.getExpiration().getTime())
                .isAtLeast(Clock.offset(TEST_CLOCK, TOKEN_LIFETIME.minusSeconds(1)).millis());
    }

    /**
     * Make sure Jwt created is formatted according to the Google Cloud IoT Core<a
     * href="https://cloud.google.com/iot/docs/how-tos/credentials/jwts#jwt_composition">spec</a>.
     */
    @Test
    public void testCreateJwtEc() throws JoseException {
        JwtGenerator jwtGenerator =
                new JwtGenerator(EC_KEY_PAIR, JWT_AUDIENCE, TOKEN_LIFETIME, TEST_CLOCK);
        String rawJwt = jwtGenerator.createJwt();

        // Validate JWT
        Jws<Claims> parsedJwt;
        try {
            parsedJwt = Jwts.parser()
                    .setSigningKey(EC_KEY_PAIR.getPublic())
                    .parseClaimsJws(rawJwt);
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException e) {
            fail("Error parsing JWT: " + e);
            return;  // Satisfy compiler
        }

        JwsHeader header = parsedJwt.getHeader();
        Claims claims = parsedJwt.getBody();

        assertThat(header.getAlgorithm()).isEqualTo("ES256");
        assertThat(header.getType()).isEqualTo("JWT");
        assertThat(claims.getAudience()).isEqualTo(JWT_AUDIENCE);

        // JWT requires time in seconds from epoch, not millis, so allow issue time within one
        // second.
        assertThat(claims.getIssuedAt().getTime()).isAtLeast(TEST_CLOCK.millis() - 1000);
        assertThat(claims.getIssuedAt().getTime()).isAtMost(TEST_CLOCK.millis() + 1000);

        // Check expiration time within one second of issue time + TOKEN_LIFETIME
        assertThat(claims.getExpiration().getTime())
                .isLessThan(Clock.offset(TEST_CLOCK, TOKEN_LIFETIME.plusSeconds(1)).millis());
        assertThat(claims.getExpiration().getTime())
                .isAtLeast(Clock.offset(TEST_CLOCK, TOKEN_LIFETIME.minusSeconds(1)).millis());
    }
}
