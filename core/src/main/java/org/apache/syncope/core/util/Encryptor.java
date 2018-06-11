/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.util;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.SyncopeConstants;
import org.apache.syncope.common.types.CipherAlgorithm;
import org.apache.syncope.core.persistence.dao.ConfDAO;
import org.apache.syncope.core.persistence.dao.NotFoundException;
import org.jasypt.commons.CommonUtils;
import org.jasypt.digest.StandardStringDigester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.codec.Base64;

public final class Encryptor {

    private static final Logger LOG = LoggerFactory.getLogger(Encryptor.class);

    private static final Map<String, Encryptor> INSTANCES = new ConcurrentHashMap<String, Encryptor>();

    private static final String DEFAULT_SECRET_KEY = "1abcdefghilmnopqrstuvz2!";

    /**
     * Default value for salted {@link StandardStringDigester#setIterations(int)}.
     */
    private static final int DEFAULT_SALT_ITERATIONS = 1;

    /**
     * Default value for {@link StandardStringDigester#setSaltSizeBytes(int)}.
     */
    private static final int DEFAULT_SALT_SIZE_BYTES = 8;

    /**
     * Default value for {@link StandardStringDigester#setInvertPositionOfPlainSaltInEncryptionResults(boolean)}.
     */
    private static final boolean DEFAULT_IPOPSIER = true;

    /**
     * Default value for salted {@link StandardStringDigester#setInvertPositionOfSaltInMessageBeforeDigesting(boolean)}.
     */
    private static final boolean DEFAULT_IPOSIMBD = true;

    /**
     * Default value for salted {@link StandardStringDigester#setUseLenientSaltSizeCheck(boolean)}.
     */
    private static final boolean DEFAULT_ULSSC = true;

    private static String secretKey;

    private static Integer saltIterations;

    private static Integer saltSizeBytes;

    private static Boolean ipopsier;

    private static Boolean iposimbd;

    private static Boolean ulssc;

    static {
        InputStream propStream = null;
        try {
            propStream = Encryptor.class.getResourceAsStream("/security.properties");
            Properties props = new Properties();
            props.load(propStream);

            secretKey = props.getProperty("secretKey");
            saltIterations = Integer.valueOf(props.getProperty("digester.saltIterations"));
            saltSizeBytes = Integer.valueOf(props.getProperty("digester.saltSizeBytes"));
            ipopsier = Boolean.valueOf(props.getProperty("digester.invertPositionOfPlainSaltInEncryptionResults"));
            iposimbd = Boolean.valueOf(props.getProperty("digester.invertPositionOfSaltInMessageBeforeDigesting"));
            ulssc = Boolean.valueOf(props.getProperty("digester.useLenientSaltSizeCheck"));
        } catch (Exception e) {
            LOG.error("Could not read security parameters", e);
        } finally {
            IOUtils.closeQuietly(propStream);
        }

        if (secretKey == null) {
            secretKey = DEFAULT_SECRET_KEY;
            LOG.debug("secretKey not found, reverting to default");
        }
        if (saltIterations == null) {
            saltIterations = DEFAULT_SALT_ITERATIONS;
            LOG.debug("digester.saltIterations not found, reverting to default");
        }
        if (saltSizeBytes == null) {
            saltSizeBytes = DEFAULT_SALT_SIZE_BYTES;
            LOG.debug("digester.saltSizeBytes not found, reverting to default");
        }
        if (ipopsier == null) {
            ipopsier = DEFAULT_IPOPSIER;
            LOG.debug("digester.invertPositionOfPlainSaltInEncryptionResults not found, reverting to default");
        }
        if (iposimbd == null) {
            iposimbd = DEFAULT_IPOSIMBD;
            LOG.debug("digester.invertPositionOfSaltInMessageBeforeDigesting not found, reverting to default");
        }
        if (ulssc == null) {
            ulssc = DEFAULT_ULSSC;
            LOG.debug("digester.useLenientSaltSizeCheck not found, reverting to default");
        }
    }

    /**
     * Get predefined password cipher algorithm from SyncopeConf.
     *
     * @return cipher algorithm.
     */
    public static CipherAlgorithm getPredefinedCipherAlgoritm() {
        ConfDAO confDAO = ApplicationContextProvider.getApplicationContext().getBean(ConfDAO.class);
        final String algorithm = confDAO.find(
                "password.cipher.algorithm", CipherAlgorithm.AES.name()).getValues().get(0).getStringValue();
        try {
            return CipherAlgorithm.valueOf(algorithm);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Cipher algorithm " + algorithm);
        }
    }

    public static Encryptor getInstance() {
        return getInstance(secretKey);
    }

    public static Encryptor getInstance(final String secretKey) {
        String actualKey = StringUtils.isBlank(secretKey) ? DEFAULT_SECRET_KEY : secretKey;

        Encryptor instance = INSTANCES.get(actualKey);
        if (instance == null) {
            instance = new Encryptor(actualKey);
            INSTANCES.put(actualKey, instance);
        }

        return instance;
    }

    private SecretKeySpec keySpec;

    private Encryptor(final String secretKey) {
        String actualKey = secretKey;
        if (actualKey.length() < 16) {
            StringBuilder actualKeyPadding = new StringBuilder(actualKey);
            int length = 16 - actualKey.length();
            String randomChars = SecureRandomUtil.generateRandomPassword(length);

            actualKeyPadding.append(randomChars);
            actualKey = actualKeyPadding.toString();
            LOG.warn("The secret key is too short (< 16), adding some random characters. "
                     + "Passwords encrypted with AES and this key will not be recoverable "
                     + "as a result if the container is restarted.");
        }

        try {
            keySpec = new SecretKeySpec(ArrayUtils.subarray(
                    actualKey.getBytes(SyncopeConstants.DEFAULT_ENCODING), 0, 16),
                    CipherAlgorithm.AES.getAlgorithm());
        } catch (Exception e) {
            LOG.error("Error during key specification", e);
        }
    }

    public String encode(final String value, final CipherAlgorithm cipherAlgorithm)
            throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {

        String encodedValue = null;

        if (value != null) {
            if (cipherAlgorithm == null || cipherAlgorithm == CipherAlgorithm.AES) {
                final byte[] cleartext = value.getBytes(SyncopeConstants.DEFAULT_ENCODING);

                final Cipher cipher = Cipher.getInstance(CipherAlgorithm.AES.getAlgorithm());
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);

                encodedValue = new String(Base64.encode(cipher.doFinal(cleartext)));
            } else if (cipherAlgorithm == CipherAlgorithm.BCRYPT) {
                encodedValue = BCrypt.hashpw(value, BCrypt.gensalt());
            } else {
                encodedValue = getDigester(cipherAlgorithm).digest(value);
            }
        }

        return encodedValue;
    }

    public boolean verify(final String value, final CipherAlgorithm cipherAlgorithm, final String encodedValue) {
        boolean res = false;

        try {
            if (value != null) {
                if (cipherAlgorithm == null || cipherAlgorithm == CipherAlgorithm.AES) {
                    res = encode(value, cipherAlgorithm).equals(encodedValue);
                } else if (cipherAlgorithm == CipherAlgorithm.BCRYPT) {
                    res = BCrypt.checkpw(value, encodedValue);
                } else {
                    res = getDigester(cipherAlgorithm).matches(value, encodedValue);
                }
            }
        } catch (Exception e) {
            LOG.error("Could not verify encoded value", e);
        }

        return res;
    }

    public String decode(final String encodedValue, final CipherAlgorithm cipherAlgorithm)
            throws UnsupportedEncodingException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {

        String value = null;

        if (encodedValue != null && cipherAlgorithm == CipherAlgorithm.AES) {
            final byte[] encoded = encodedValue.getBytes(SyncopeConstants.DEFAULT_ENCODING);

            final Cipher cipher = Cipher.getInstance(CipherAlgorithm.AES.getAlgorithm());
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            value = new String(cipher.doFinal(Base64.decode(encoded)), SyncopeConstants.DEFAULT_ENCODING);
        }

        return value;
    }

    private StandardStringDigester getDigester(final CipherAlgorithm cipherAlgorithm) {
        StandardStringDigester digester = new StandardStringDigester();

        if (cipherAlgorithm.getAlgorithm().startsWith("S-")) {
            // Salted ...
            digester.setAlgorithm(cipherAlgorithm.getAlgorithm().replaceFirst("S\\-", ""));
            digester.setIterations(saltIterations);
            digester.setSaltSizeBytes(saltSizeBytes);
            digester.setInvertPositionOfPlainSaltInEncryptionResults(ipopsier);
            digester.setInvertPositionOfSaltInMessageBeforeDigesting(iposimbd);
            digester.setUseLenientSaltSizeCheck(ulssc);
        } else {
            // Not salted ...
            digester.setAlgorithm(cipherAlgorithm.getAlgorithm());
            digester.setIterations(1);
            digester.setSaltSizeBytes(0);
        }

        digester.setStringOutputType(CommonUtils.STRING_OUTPUT_TYPE_HEXADECIMAL);
        return digester;
    }
}