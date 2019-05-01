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

package org.nexial.core.excel.ext;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import static org.nexial.core.NexialConst.TOKEN_END;
import static org.nexial.core.NexialConst.TOKEN_START;
import static org.nexial.core.excel.ext.CipherHelper.CRYPT_IND;

public final class CellTextReader {
    private static final Map<String, String> DECRYPT_CRYPT = new HashMap<>();
    private static final Map<String, String> ORIGINALS = new HashMap<>();
    private static final CipherHelper CIPHER = new CipherHelper();

    private CellTextReader() {}

    public static String getText(String cellString) {
        if (!StringUtils.startsWith(cellString, CRYPT_IND)) { return cellString; }

        try {
            // apply magic here
            String decrypt = CIPHER.decrypt(cellString);
            DECRYPT_CRYPT.put(decrypt, cellString);
            return decrypt;
        } catch (Exception e) {
            return cellString;
        }
    }

    public static String getOriginal(String original, String decrypted) {
        String var = stripTokenSymbols(original);
        return ORIGINALS.containsKey(var) ? original : decrypted;
    }

    public static String readValue(String cellString) {
        if (DECRYPT_CRYPT.containsKey(cellString)) { return DECRYPT_CRYPT.get(cellString); }
        return cellString;
    }

    public static boolean isCrypt(String data) { return DECRYPT_CRYPT.containsKey(data); }

    public static void unsetValue(String cellString) {
        DECRYPT_CRYPT.remove(cellString);
    }

    public static void registerCrypt(String varName, String original, String decrypted) {
        if (StringUtils.isBlank(varName)) { return; }
        if (StringUtils.isBlank(original)) { return; }
        if (StringUtils.isBlank(decrypted)) { return; }

        varName = stripTokenSymbols(varName);
        ORIGINALS.put(varName, decrypted);
        DECRYPT_CRYPT.put(decrypted, original);
    }

    protected static String stripTokenSymbols(String varName) {
        if (StringUtils.startsWith(varName, TOKEN_START) && StringUtils.endsWith(varName, TOKEN_END)) {
            varName = StringUtils.substringBetween(varName, TOKEN_START, TOKEN_END);
        }
        return varName;
    }
}
