package com.uniace.trackr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * Turns a COSE_Key (the public key format WebAuthn authenticators emit) into a
 * standard JCA {@link PublicKey}.
 *
 * <p>Only the two algorithms browsers actually use for platform authenticators
 * are supported: ES256 (ECDSA on P-256) and RS256. Anything else is rejected
 * rather than silently accepted.
 */
public final class CoseKeyParser {

    /** Jackson can read CBOR maps with integer keys, exposing them as "1", "-2", ... */
    private static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory());

    private static final int KTY_EC2 = 2;
    private static final int KTY_RSA = 3;

    public static final int ALG_ES256 = -7;
    public static final int ALG_RS256 = -257;

    private CoseKeyParser() {
    }

    public record ParsedKey(PublicKey publicKey, String algorithmName, String jcaSignatureAlgorithm) {
    }

    public static ParsedKey parse(byte[] coseKey) {
        JsonNode node;
        try {
            node = CBOR.readTree(coseKey);
        } catch (Exception ex) {
            throw new IllegalArgumentException("The credential's public key could not be read", ex);
        }

        int kty = requireInt(node, "1", "key type");
        int alg = requireInt(node, "3", "algorithm");

        try {
            if (kty == KTY_EC2 && alg == ALG_ES256) {
                int curve = requireInt(node, "-1", "curve");
                if (curve != 1) {
                    throw new IllegalArgumentException("Only the P-256 curve is supported");
                }
                BigInteger x = positive(binary(node, "-2"));
                BigInteger y = positive(binary(node, "-3"));

                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec p256 = params.getParameterSpec(ECParameterSpec.class);

                PublicKey key = KeyFactory.getInstance("EC")
                        .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256));
                return new ParsedKey(key, "ES256", "SHA256withECDSA");
            }

            if (kty == KTY_RSA && alg == ALG_RS256) {
                BigInteger modulus = positive(binary(node, "-1"));
                BigInteger exponent = positive(binary(node, "-2"));
                PublicKey key = KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
                return new ParsedKey(key, "RS256", "SHA256withRSA");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("The credential's public key could not be used", ex);
        }

        throw new IllegalArgumentException(
                "Unsupported credential algorithm (key type " + kty + ", algorithm " + alg + ")");
    }

    /** Signature algorithm to use for a credential already stored with this name. */
    public static String jcaSignatureAlgorithm(String algorithmName) {
        return switch (algorithmName) {
            case "ES256" -> "SHA256withECDSA";
            case "RS256" -> "SHA256withRSA";
            default -> throw new IllegalArgumentException("Unsupported credential algorithm: " + algorithmName);
        };
    }

    public static String keyFactoryAlgorithm(String algorithmName) {
        return switch (algorithmName) {
            case "ES256" -> "EC";
            case "RS256" -> "RSA";
            default -> throw new IllegalArgumentException("Unsupported credential algorithm: " + algorithmName);
        };
    }

    private static int requireInt(JsonNode node, String field, String label) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("The credential's public key is missing its " + label);
        }
        return value.asInt();
    }

    private static byte[] binary(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw new IllegalArgumentException("The credential's public key is incomplete");
        }
        try {
            return value.binaryValue();
        } catch (Exception ex) {
            throw new IllegalArgumentException("The credential's public key is incomplete", ex);
        }
    }

    /** COSE encodes these as unsigned big-endian bytes; the sign bit must not leak in. */
    private static BigInteger positive(byte[] bytes) {
        return new BigInteger(1, bytes);
    }
}
