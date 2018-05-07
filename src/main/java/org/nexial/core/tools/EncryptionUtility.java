/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.tools;

import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.nexial.core.NexialConst.ENCRYPTION_ALGORITHM;
import static org.nexial.core.NexialConst.RAND_SEED_SIZE;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;

/**
 * Contain utility methods for dealing with Encryption.
 */
public class EncryptionUtility {
    /**
     * Retrieves the secrets from the encryptedFile specified by decrypting the content.
     *
     * @return {@link Map} of secrets encrypted.
     */
    public static Map<String, String> retrieveEncryptedSecrets(final String encryptedContent, final byte[] key)
        throws DecoderException, GeneralSecurityException, IOException {

        String keyStr = new String(key);
        byte[] keyDecoded = Hex.decodeHex(keyStr.toCharArray());

        Key secret = new SecretKeySpec(keyDecoded, ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(DECRYPT_MODE, secret);

        String configuration = decrypt(encryptedContent, cipher);
        Map<String, String> secrets = new HashMap<>();

        final Properties p = new Properties();
        p.load(new StringReader(configuration));

        p.forEach((x, y) -> secrets.put(x.toString(), y.toString()));

        return secrets;
    }

    /**
     * Encrypts the given content using the secret key.
     *
     * @param content        content to be encrypted.
     * @param secretKeyValue key with which files needs to be encrypted.
     * @return encrypted content.
     */
    public static String encryptContent(@NotNull String content,
                                        @NotNull final byte[] secretKeyValue)
        throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
               IllegalBlockSizeException, BadPaddingException {
        Key secretKey = new SecretKeySpec(secretKeyValue, ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(ENCRYPT_MODE, secretKey);

        final String randomSeed = RandomStringUtils.randomAlphanumeric(RAND_SEED_SIZE);
        content = randomSeed.concat(content).concat(StringUtils.reverse(randomSeed));

        byte[] encrypted = cipher.doFinal(content.getBytes());
        return CRYPT_IND + StringUtils.reverse(Hex.encodeHexString(encrypted));
    }

    /**
     * Decrypts the encrypted content with the {@link Cipher} passed in.
     *
     * @param encrypted encrypted content.
     * @param cipher    {@link Cipher}
     * @return decrypted text.
     */
    // todo: direct copy from CipherHelper; why can't we reuse what we already have!?
    private static String decrypt(String encrypted, Cipher cipher) throws GeneralSecurityException, DecoderException {
        if (StringUtils.isBlank(encrypted) || !StringUtils.startsWith(encrypted, CRYPT_IND)) {
            return encrypted;
        }

        String encryptByteString = StringUtils.reverse(StringUtils.substringAfter(encrypted, CRYPT_IND));
        byte[] encryptedBytes = Hex.decodeHex(encryptByteString.toCharArray());

        String decrypted = new String(cipher.doFinal(encryptedBytes));
        if (StringUtils.length(decrypted) < (RAND_SEED_SIZE * 2 + 1)) {
            throw new DecoderException("Invalid encrypted text: " + encrypted + "; invalid length");
        }

        // sift out spice
        String leftSpice = StringUtils.substring(decrypted, 0, RAND_SEED_SIZE);
        String rightSpice = StringUtils.substring(decrypted, decrypted.length() - RAND_SEED_SIZE, decrypted.length());
        if (!StringUtils.equals(leftSpice, StringUtils.reverse(rightSpice))) {
            throw new DecoderException("Invalid encrypted text: " + encrypted + "; crypt key mismatched");
        }

        return StringUtils.substring(decrypted, RAND_SEED_SIZE, decrypted.length() - RAND_SEED_SIZE);
    }
}
