/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.excel.ext;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

public final class CipherHelper {
    public static final String CRYPT_IND = "crypt:";

    private static final byte[] IV = ("Se" + "ntr" + "y#1").getBytes();
    private static final IvParameterSpec SPEC = new IvParameterSpec(IV);
    private static final byte[] SECRET = ("Se" + "nt" + "ryWil" + "lRoc" + "kYo" + "urWo" + "rld!").getBytes();
    private static final String DEF_ALGORITHM = "DESede";
    private static final String DEF_ALGORITHM_SCHEME = DEF_ALGORITHM + "/CBC/PKCS5Padding";
    private static final int RAND_SEED_SIZE = 5;

    private Cipher encryptor;
    private Cipher decryptor;

    public CipherHelper() {
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DEF_ALGORITHM);
            SecretKey key = keyFactory.generateSecret(new DESedeKeySpec(SECRET));

            encryptor = Cipher.getInstance(DEF_ALGORITHM_SCHEME);
            encryptor.init(ENCRYPT_MODE, key, SPEC);

            decryptor = Cipher.getInstance(DEF_ALGORITHM_SCHEME);
            decryptor.init(DECRYPT_MODE, key, SPEC);
        } catch (Exception e) {
            String error = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new RuntimeException("Error initializing application: " + error);
        }
    }

    public String encrypt(String plainText) throws GeneralSecurityException { return encrypt(plainText, encryptor); }

    public String encrypt(String plainText, Cipher cipher) throws GeneralSecurityException {
        if (StringUtils.isBlank(plainText)) { return plainText; }

        // spice it up
        String randomSeed = getRandomAlphaNumber(RAND_SEED_SIZE);
        plainText = randomSeed + plainText + StringUtils.reverse(randomSeed);

        byte[] encrypted = cipher.doFinal(plainText.getBytes());
        return CRYPT_IND + StringUtils.reverse(Hex.encodeHexString(encrypted));
    }

    public String decrypt(String encrypted, Cipher cipher)
        throws GeneralSecurityException, DecoderException {
        if (StringUtils.isBlank(encrypted) || !StringUtils.startsWith(encrypted, CRYPT_IND)) { return encrypted; }

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

    String decrypt(String encrypted) throws GeneralSecurityException, DecoderException {
        return decrypt(encrypted, decryptor);
    }

    private String getRandomAlphaNumber(int length) { return RandomStringUtils.randomAlphanumeric(length); }
}
