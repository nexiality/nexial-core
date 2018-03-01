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

package org.nexial.commons.utils;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;
import static java.io.File.separator;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static org.apache.commons.codec.binary.Hex.decodeHex;
import static org.apache.commons.lang.StringUtils.EMPTY;
import static org.apache.commons.lang.StringUtils.substringBeforeLast;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

/**
 * Contains utility methods for dealing with Encryption.
 */
public class EncryptionUtility {
    private static final String NEW_LINE_SEPARATOR = "\n";
    private static final String PROPERTY_SEPARATOR = "=";

    /**
     * Retrieves the secrets from the encryptedFile specified by decrypting the content.
     *
     * @return {@link Map} of secrets encrypted.
     * @throws {@link IOException}
     * @throws {@link DecoderException}
     * @throws {@link GeneralSecurityException}
     */
    public static Map<String, String> retrieveEncryptedSecrets(final String fileContent) throws IOException,
            DecoderException, GeneralSecurityException {

        final String secretKeyEncrypted = substringAfter(fileContent, SECRET_CONTENT_SEPARATOR).trim();

        InputStream secretKeyInputStream =
                EncryptionUtility.class.getClassLoader().getResourceAsStream(SECRET_KEY_FILE);
        final byte[] publicKeyBytes = IOUtils.toByteArray(secretKeyInputStream);

        Key secret = new SecretKeySpec(publicKeyBytes, ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(DECRYPT_MODE, secret);

        String privateKey = decrypt(secretKeyEncrypted, cipher);
        final String encryptedContent = substringBefore(fileContent, SECRET_CONTENT_SEPARATOR);

        secret = new SecretKeySpec(privateKey.getBytes(), ENCRYPTION_ALGORITHM);
        cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(DECRYPT_MODE, secret);

        String properties = decrypt(encryptedContent, cipher);

        Map<String, String> secrets = new HashMap<>();

        Arrays.stream(properties.split(NEW_LINE_SEPARATOR)).forEach(property -> {
            if (StringUtils.isNotBlank(property.trim())) {
                String[] keys = property.split(PROPERTY_SEPARATOR);
                secrets.put(keys[0], keys[1]);
            }
        });
        return secrets;
    }

    /**
     * Decrypts the encrypted content with the {@link Cipher} passed in.
     *
     * @param encrypted encrypted content.
     * @param cipher    {@link Cipher}
     * @return decrypted text.
     * @throws {@link GeneralSecurityException}
     * @throws {@link DecoderException}
     */
    private static String decrypt(String encrypted, Cipher cipher) throws GeneralSecurityException, DecoderException {
        if (StringUtils.isBlank(encrypted) || !StringUtils.startsWith(encrypted, CRYPT_IND)) {
            return encrypted;
        }

        String encryptByteString = StringUtils.reverse(StringUtils.substringAfter(encrypted, CRYPT_IND));
        byte[] encryptedBytes = decodeHex(encryptByteString.toCharArray());

        String decrypted = new String(cipher.doFinal(encryptedBytes));
        if (StringUtils.length(decrypted) < (RAND_SEED_SIZE * 2 + 1)) {
            throw new DecoderException("Invalid encrypted text: " + encrypted + "; invalid length");
        }

        // sift out spice
        String leftSpice = StringUtils.substring(decrypted, 0, RAND_SEED_SIZE);
        String rightSpice = StringUtils.substring(decrypted,
                decrypted.length() - RAND_SEED_SIZE, decrypted.length());
        if (!StringUtils.equals(leftSpice, StringUtils.reverse(rightSpice))) {
            throw new DecoderException("Invalid encrypted text: " + encrypted + "; crypt key mismatched");
        }

        return StringUtils.substring(decrypted, RAND_SEED_SIZE, decrypted.length() - RAND_SEED_SIZE);
    }

    /**
     * This method checks for the location of the secret files and the corresponding keys. Initially it checks in the
     * current folder passed in. If it is not available it keeps checking recursively in the parent directories till it
     * reaches the home folder. In case the files are not available till this point, it checks in the operating system
     * user directory. The folder path where the files are found is returned as the result. In case it is found no where
     * {@link StringUtils#EMPTY} is passed as the output.
     *
     * @param projectLocation the location of the project which is currently run.
     * @return path of the folder where files are found else {@link StringUtils#EMPTY}.
     */
    public static String getSecretLocation(@NotNull String projectLocation) {
        if (projectLocation == null) {
            return EMPTY;
        }

        try {
            if (secretFilesExists(projectLocation)) return projectLocation;
            else if (projectLocation.contains(separator)) {
                return getSecretLocation(substringBeforeLast(projectLocation, separator));
            }
        } catch (SecurityException se) {
            // Indication that the folder has readonly access.
        }
        return getDefaultPath();
    }

    /**
     * Checks if the user directory path contains secret file or not. If yes the user directory path is returned else
     * the path of the secret files with in the directory path is returned.
     *
     * @return default secret path.
     */
    private static String getDefaultPath() {
        String userDirectory = System.getProperty(USER_OS_HOME);
        return secretFilesExists(userDirectory) ? userDirectory : EMPTY;
    }

    /**
     * Checks whether the data file exists in the current location or not and returns true or false
     * accordingly. Throws {@link SecurityException} if there are no permissions to access the file/folder.
     *
     * @param currentLocation path of the folder where the files are checked.
     * @return true or false based on files exist in the currentLocation or not.
     * @throws {@link SecurityException}.
     */
    private static boolean secretFilesExists(@NotNull final String currentLocation) throws SecurityException {
        return new File(join(currentLocation, separator, SECRET_FILE)).exists();
    }
}
