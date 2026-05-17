/*
 * Copyright 2026 Gary Ginzburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsginzburg.shared.util;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.UUID;

/**
 * Base62 codec for UUID values.
 *
 * A UUID is 128 bits. ceil(log62(2^128)) = 22 characters, so every UUID encodes to
 * exactly 22 URL-safe alphanumeric characters (zero-padded on the left).
 *
 * Alphabet: 0–9, A–Z, a–z  (lexicographic order within each group).
 */
public final class Base62 {

    public static final int ENCODED_LENGTH = 22;

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(62);

    private Base62() {}

    public static String encode(UUID uuid) {
        BigInteger n = toUnsignedBigInteger(uuid);
        char[] buf = new char[ENCODED_LENGTH];
        Arrays.fill(buf, '0');
        int pos = ENCODED_LENGTH - 1;
        while (n.signum() > 0) {
            BigInteger[] dr = n.divideAndRemainder(BASE);
            buf[pos--] = ALPHABET.charAt(dr[1].intValue());
            n = dr[0];
        }
        return new String(buf);
    }

    public static UUID decode(String encoded) {
        if (encoded == null || encoded.length() != ENCODED_LENGTH) {
            throw new IllegalArgumentException(
                    "Base62 UUID must be exactly " + ENCODED_LENGTH + " characters, got: " + encoded);
        }
        BigInteger n = BigInteger.ZERO;
        for (char c : encoded.toCharArray()) {
            int idx = ALPHABET.indexOf(c);
            if (idx < 0) throw new IllegalArgumentException("Invalid base62 character: '" + c + "'");
            n = n.multiply(BASE).add(BigInteger.valueOf(idx));
        }
        return fromUnsignedBigInteger(n);
    }

    /** Returns true if the string looks like a valid base62-encoded UUID (22 alnum chars). */
    public static boolean isValid(String s) {
        if (s == null || s.length() != ENCODED_LENGTH) return false;
        for (char c : s.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return false;
        }
        return true;
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private static BigInteger toUnsignedBigInteger(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 7; i >= 0; i--) { bytes[i] = (byte) (msb & 0xFF); msb >>= 8; }
        for (int i = 15; i >= 8; i--) { bytes[i] = (byte) (lsb & 0xFF); lsb >>= 8; }
        return new BigInteger(1, bytes); // sign=1 → always positive
    }

    private static UUID fromUnsignedBigInteger(BigInteger n) {
        byte[] raw = n.toByteArray();
        byte[] b16 = new byte[16];
        // raw may have a leading 0x00 sign byte or be shorter than 16 bytes
        int src  = raw.length > 16 ? raw.length - 16 : 0;
        int dest = raw.length < 16 ? 16 - raw.length : 0;
        System.arraycopy(raw, src, b16, dest, Math.min(raw.length, 16));
        long msb = 0, lsb = 0;
        for (int i = 0;  i < 8;  i++) msb = (msb << 8) | (b16[i] & 0xFF);
        for (int i = 8;  i < 16; i++) lsb = (lsb << 8) | (b16[i] & 0xFF);
        return new UUID(msb, lsb);
    }
}
