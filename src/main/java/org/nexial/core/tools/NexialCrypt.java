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

package org.nexial.core.tools;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.excel.ext.CipherHelper;

import java.security.GeneralSecurityException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * provide basic utility for nexial user to scramble sensitive information such as password
 */
final class NexialCrypt {
    private NexialCrypt() { }

    public static void main(String[] args) throws Exception {
        NexialCrypt crypt = new NexialCrypt();
        if (ArrayUtils.isEmpty(args)) {
            crypt.doInteractive();
        } else {
            crypt.processInput(args[0]);
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    protected void doInteractive() throws GeneralSecurityException {
        Scanner consoleInput = new Scanner(System.in);
        System.out.print("enter plain text    > ");
        try {
            String input = consoleInput.nextLine();
            processInput(input);
        } catch (NoSuchElementException e) {
            System.err.println("No input provided");
        }
    }

    @SuppressWarnings("PMD.SystemPrintln")
    protected void processInput(String plainText) throws GeneralSecurityException {
        if (StringUtils.isBlank(plainText)) {
            System.err.println("!!! ERROR !!! No input found.");
            System.exit(-1);
            return;
        }

        System.out.print(new CipherHelper().encrypt(plainText));
    }

}
