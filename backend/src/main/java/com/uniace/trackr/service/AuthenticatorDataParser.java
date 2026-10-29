package com.uniace.trackr.service;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Parses the {@code authenticatorData} byte string defined by the WebAuthn
 * specification:
 *
 * <pre>
 *   rpIdHash        32 bytes
 *   flags            1 byte   (bit 0 UP, bit 2 UV, bit 6 AT, bit 7 ED)
 *   signCount        4 bytes  big endian
 *   [attestedCredentialData]  present when the AT flag is set:
 *       aaguid          16 bytes
 *       credentialIdLen  2 bytes big endian
 *       credentialId     credentialIdLen bytes
 *       credentialPublicKey  COSE_Key, variable length
 * </pre>
 */
public final class AuthenticatorDataParser {

    private static final int FLAG_USER_PRESENT = 0x01;
    private static final int FLAG_USER_VERIFIED = 0x04;
    private static final int FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;

    private AuthenticatorDataParser() {
    }

    public record AuthenticatorData(
            byte[] rpIdHash,
            boolean userPresent,
            boolean userVerified,
            long signCount,
            byte[] aaguid,
            byte[] credentialId,
            byte[] credentialPublicKey
    ) {
    }

    public static AuthenticatorData parse(byte[] data) {
        if (data == null || data.length < 37) {
            throw new IllegalArgumentException("The device's response was incomplete");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] rpIdHash = new byte[32];
        buffer.get(rpIdHash);

        int flags = buffer.get() & 0xFF;
        long signCount = Integer.toUnsignedLong(buffer.getInt());

        byte[] aaguid = null;
        byte[] credentialId = null;
        byte[] credentialPublicKey = null;

        if ((flags & FLAG_ATTESTED_CREDENTIAL_DATA) != 0) {
            if (buffer.remaining() < 18) {
                throw new IllegalArgumentException("The device's response was incomplete");
            }
            aaguid = new byte[16];
            buffer.get(aaguid);

            int credentialIdLength = buffer.getShort() & 0xFFFF;
            if (credentialIdLength > buffer.remaining()) {
                throw new IllegalArgumentException("The device's response was incomplete");
            }
            credentialId = new byte[credentialIdLength];
            buffer.get(credentialId);

            // Whatever remains is the COSE public key (plus optional extensions,
            // which the CBOR reader stops before).
            credentialPublicKey = Arrays.copyOfRange(data, data.length - buffer.remaining(), data.length);
        }

        return new AuthenticatorData(
                rpIdHash,
                (flags & FLAG_USER_PRESENT) != 0,
                (flags & FLAG_USER_VERIFIED) != 0,
                signCount,
                aaguid,
                credentialId,
                credentialPublicKey);
    }

    public static String formatAaguid(byte[] aaguid) {
        if (aaguid == null || aaguid.length != 16) {
            return null;
        }
        StringBuilder sb = new StringBuilder(36);
        for (int i = 0; i < 16; i++) {
            if (i == 4 || i == 6 || i == 8 || i == 10) {
                sb.append('-');
            }
            sb.append(String.format("%02x", aaguid[i]));
        }
        return sb.toString();
    }
}
