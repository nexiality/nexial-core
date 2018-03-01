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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.validation.constraints.NotNull;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.NexialConst;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;
import static org.nexial.core.tools.CliUtils.newArgOption;

/**
 * This class is a utility for encrypting the secret/data file with the given key file passed as options
 * in the commandline.
 */
public final class SecretEncryptor {
    private static final String OPT_DATA_FILE = "f";
    private static final String OPT_SECRET_KEY = "k";

    /**
     * Receives the data file as -f parameter and secret file as -k parameter and encrypts the data file with the secret
     * file.
     * <p>
     * The following steps are executed:-<br/>
     * <ol>
     * <li>The content of the file passed as the option through command line is first read as a String.</li>
     * <li>The file content is encrypted using the private key i.e the key passed as -k Option.</li>
     * <li>The content is appended with {@link NexialConst#SECRET_CONTENT_SEPARATOR} and secretKey.</li>
     * <li>This content is appended with new the value obtained after encrypting the private key with public key </li>
     * </ol>
     * <p>
     * In case of any Exception the system exits displaying the appropriate message.
     *
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        Options cmdOptions = new Options();
        cmdOptions.addOption(newArgOption(OPT_DATA_FILE, "file", "The file containing sensitive data to encrypt."));
        cmdOptions.addOption(newArgOption(OPT_SECRET_KEY, "secretKey", "The key to encrypt/decrypt sensitive data."));

        CommandLineParser parser = new DefaultParser();
        SecretEncryptor secretEncryptor = new SecretEncryptor();

        try {
            CommandLine cmd = parser.parse(cmdOptions, args);

            final String dataFilePath = cmd.getOptionValue(OPT_DATA_FILE);
            final String secretKeyValue = cmd.getOptionValue(OPT_SECRET_KEY);

            secretEncryptor.checkValidFilePath(dataFilePath);

            String fileContent = new String(Files.readAllBytes(Paths.get(dataFilePath)));
            String encryptedValue = secretEncryptor.encryptContent(fileContent, secretKeyValue);
            encryptedValue = encryptedValue.concat(SECRET_CONTENT_SEPARATOR);

            Path publicKeyPath = Paths.get(ClassLoader.getSystemResource(SECRET_KEY_FILE).toURI());

            final String publicKey = new String(Files.readAllBytes(publicKeyPath)).trim();

            final String secretKeyEncryptedValue = secretEncryptor.encryptContent(secretKeyValue, publicKey);

            encryptedValue = encryptedValue.concat(secretKeyEncryptedValue);
            Files.write(Paths.get(dataFilePath), encryptedValue.getBytes());

        } catch (IOException | ParseException | URISyntaxException | GeneralSecurityException e) {
            e.printStackTrace();
            System.err.println("Exception occurred while processing. Exception message is " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Encrypts the given content using the secret key.
     *
     * @param content        content to be encrypted.
     * @param secretKeyValue key with which files needs to be encrypted.
     * @return encrypted content.
     */
    protected String encryptContent(@NotNull String content,
                                    @NotNull final String secretKeyValue)
        throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
               IllegalBlockSizeException, BadPaddingException {
        Key secretKey = new SecretKeySpec(secretKeyValue.getBytes(), ENCRYPTION_ALGORITHM);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
        cipher.init(ENCRYPT_MODE, secretKey);

        final String randomSeed = randomAlphanumeric(RAND_SEED_SIZE);
        content = randomSeed.concat(content).concat(StringUtils.reverse(randomSeed));

        byte[] encrypted = cipher.doFinal(content.getBytes());
        return CRYPT_IND + StringUtils.reverse(Hex.encodeHexString(encrypted));
    }

    /**
     * Checks if the filePath passed in specifies a valid file which exists as well as having write permissions to
     * overwrite. If not the program exits with appropriate message.
     *
     * @param filePath path of the file.
     */
    private void checkValidFilePath(@NotNull final String filePath) {
        final File file = new File(filePath);
        if (StringUtils.isEmpty(filePath) || !file.exists()) {
            System.err.println("Data file provided is not available.");
            System.exit(-1);
        } else if (!file.canWrite()) {
            System.err.println("File is not having write permissions. Please provide a valid file.");
            System.exit(-1);
        }
    }
}
