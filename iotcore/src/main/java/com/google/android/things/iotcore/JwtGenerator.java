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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;


/**
 * This class wraps the storage and access of the authentication key used for Cloud IoT. One of the
 * driving reasons for this is to leverage the secure key storage on Android Things.
 */
class JwtGenerator {
    private static final String RSA_ALGORITHM = "RSA";
    private static final String EC_ALGORITHM = "EC";
    private final Duration mTokenLifetime;
    private final Clock mClock;
    private final JsonWebSignature mJws;
    private final JwtClaims mClaims;

    /**
     * Create a JwtGenerator instance.
     *
     * @param keyPair the keys to use for signing JWTs
     * @param jwtAudience the audience to use in generated JWTs
     * @param tokenLifetime the amount of time generated JWTs should be valid
     */
    JwtGenerator(
            @NonNull KeyPair keyPair,
            @NonNull String jwtAudience,
            @NonNull Duration tokenLifetime) {
        this(keyPair, jwtAudience, tokenLifetime, Clock.systemUTC());
    }

    private static void checkNotNull(Object ref, String tag) {
        if (ref == null) {
            throw new NullPointerException(tag + " cannot be null");
        }
    }

    @VisibleForTesting()
    JwtGenerator(
            @NonNull KeyPair keyPair,
            @NonNull String jwtAudience,
            @NonNull Duration tokenLifetime,
            @NonNull Clock clock) {
        checkNotNull(keyPair, "keypair");
        checkNotNull(jwtAudience, "JWT audience");
        checkNotNull(tokenLifetime, "Token lifetime");
        checkNotNull(clock, "Clock");

        String algorithm = keyPair.getPrivate().getAlgorithm();
        if (!algorithm.equals(RSA_ALGORITHM) && !algorithm.equals(EC_ALGORITHM)) {
            throw new IllegalArgumentException("Keys use unsupported algorithm.");
        }

        mTokenLifetime = tokenLifetime;
        mClock = clock;

        mJws = new JsonWebSignature();
        mJws.setAlgorithmHeaderValue(algorithm.equals("RSA")
                ? AlgorithmIdentifiers.RSA_USING_SHA256
                : AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256);
        mJws.setHeader("typ", "JWT");
        mJws.setKey(keyPair.getPrivate());

        mClaims = new JwtClaims();
        mClaims.setAudience(jwtAudience);
    }

    /**
     * Create JSON web token for a Google Cloud IoT project.
     *
     * @return JWT for project
     */
    String createJwt() throws JoseException {
        Instant now = mClock.instant();

        mClaims.setIssuedAt(NumericDate.fromMilliseconds(now.toEpochMilli()));
        mClaims.setExpirationTime(
                NumericDate.fromMilliseconds(now.plus(mTokenLifetime).toEpochMilli()));

        mJws.setPayload(mClaims.toJson());
        return mJws.getCompactSerialization();
    }
}
