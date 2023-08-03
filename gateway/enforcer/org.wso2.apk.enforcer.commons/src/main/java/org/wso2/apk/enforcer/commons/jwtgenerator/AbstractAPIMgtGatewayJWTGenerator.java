/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.apk.enforcer.commons.jwtgenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.apk.enforcer.commons.constants.JWTConstants;
import org.wso2.apk.enforcer.commons.dto.ClaimValueDTO;
import org.wso2.apk.enforcer.commons.dto.JWTConfigurationDto;
import org.wso2.apk.enforcer.commons.dto.JWTInfoDto;
import org.wso2.apk.enforcer.commons.exception.JWTGeneratorException;
import org.wso2.apk.enforcer.commons.util.JWTUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for jwt generation.
 */
public abstract class AbstractAPIMgtGatewayJWTGenerator {
    private static final Log log = LogFactory.getLog(AbstractAPIMgtGatewayJWTGenerator.class);
    public static final String NONE = "NONE";
    public static final String SHA256_WITH_RSA = "SHA256withRSA";
    public static final String API_GATEWAY_ID = "wso2.org/products/am";
    public JWTConfigurationDto jwtConfigurationDto;

    private static volatile long ttl = -1L;
    public String dialectURI;

    public String signatureAlgorithm;

    public AbstractAPIMgtGatewayJWTGenerator() {
    }

    public void setJWTConfigurationDto(JWTConfigurationDto jwtConfigurationDto) {
        this.jwtConfigurationDto = jwtConfigurationDto;
        dialectURI = jwtConfigurationDto.getConsumerDialectUri();
        if (dialectURI == null) {
            dialectURI = "http://wso2.org/claims";
        }
        signatureAlgorithm = jwtConfigurationDto.getSignatureAlgorithm();
        if (!(NONE.equals(signatureAlgorithm)
                || SHA256_WITH_RSA.equals(signatureAlgorithm))) {
            signatureAlgorithm = SHA256_WITH_RSA;
        }
    }

    public JWTConfigurationDto getJWTConfigurationDto() {
        return jwtConfigurationDto;
    }
    public String generateToken(JWTInfoDto jwtInfoDto) throws JWTGeneratorException {

        String jwtHeader = buildHeader();
        String jwtBody = buildBody(jwtInfoDto);
        String base64UrlEncodedHeader = "";
        if (jwtHeader != null) {
            base64UrlEncodedHeader = encode(jwtHeader.getBytes(Charset.defaultCharset()));
        }
        String base64UrlEncodedBody = "";
        try {
            base64UrlEncodedBody = encode(jwtBody.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.debug("Error in encoding jwt body", e);
        }
        if (SHA256_WITH_RSA.equals(signatureAlgorithm)) {
            String assertion = base64UrlEncodedHeader + '.' + base64UrlEncodedBody;

            //get the assertion signed
            byte[] signedAssertion = signJWT(assertion);

            if (log.isDebugEnabled()) {
                log.debug("signed assertion value : " + new String(signedAssertion, Charset.defaultCharset()));
            }
            String base64UrlEncodedAssertion = encode(signedAssertion);

            return base64UrlEncodedHeader + '.' + base64UrlEncodedBody + '.' + base64UrlEncodedAssertion;
        } else {
            return base64UrlEncodedHeader + '.' + base64UrlEncodedBody + '.';
        }
    }

    public String buildHeader() throws JWTGeneratorException {
        String jwtHeader = null;

        if (NONE.equals(signatureAlgorithm)) {
            PlainHeader plainHeader = new PlainHeader.Builder().build();
            jwtHeader= plainHeader.toString();

        } else if (SHA256_WITH_RSA.equals(signatureAlgorithm)) {
            jwtHeader= addCertToHeader();
        }
        return jwtHeader;
    }

    public byte[] signJWT(String assertion) throws JWTGeneratorException {

        try {
            PrivateKey privateKey = jwtConfigurationDto.getPrivateKey();
            return JWTUtil.signJwt(assertion, privateKey, signatureAlgorithm);
        } catch (Exception e) {
            throw new JWTGeneratorException(e);
        }
    }

    /**
     * Helper method to add public certificate to JWT_HEADER to signature verification.
     *
     * @throws JWTGeneratorException
     */
    protected String addCertToHeader() throws JWTGeneratorException {

        try {
            return JWTUtil.generateHeader(jwtConfigurationDto, signatureAlgorithm);
        } catch (Exception e) {
            String error = "Error in obtaining keystore";
            throw new JWTGeneratorException(error, e);
        }
    }

    public String buildBody(JWTInfoDto jwtInfoDto) {

        JWTClaimsSet.Builder jwtClaimSetBuilder = new JWTClaimsSet.Builder();
        Map<String, Object> claims = populateStandardClaims(jwtInfoDto);
        Map<String, ClaimValueDTO> customClaims = populateCustomClaims(jwtInfoDto);
        for (Map.Entry<String, ClaimValueDTO> claimEntry : customClaims.entrySet()) {
            Object val = claimEntry.getValue().getValue();
            final String type = claimEntry.getValue().getType();
            Object claim = val;
            if(val instanceof String) {
                if(claimEntry.getValue().getType() != null) {
                    switch (type.toLowerCase()) {
                        case "bool":
                            claim = Boolean.parseBoolean(String.valueOf(val));
                            break;
                        case "int":
                            claim = Integer.parseInt(String.valueOf(val));
                            break;
                        case "long":
                            claim =Long.parseLong(String.valueOf(val));
                            break;
                        case "float":
                            claim = Double.parseDouble(String.valueOf(val));
                            break;
                        case "date":
                            claim = LocalDate.parse(String.valueOf(val));
                            break;
                    }
                }
            }
            if (!claims.containsKey(claimEntry.getKey())) {
                claims.put(claimEntry.getKey(),claim);
            } else {
                claims.replace(claimEntry.getKey(), claim);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        for (Map.Entry<String, Object> claimEntry : claims.entrySet()) {
            Object claimVal = claimEntry.getValue();
            if (claimVal instanceof String && claimEntry.toString().contains("{")) {
                try {
                    Map<String, String> map = mapper.readValue(claimVal.toString(), Map.class);
                    jwtClaimSetBuilder.claim(claimEntry.getKey(), map);
                } catch (IOException e) {
                    // Exception isn't thrown in order to generate jwt without claim, even if an error is
                    // occurred during the retrieving claims.
                    log.error(String.format("Error while reading claim values for %s", claimVal), e);
                }
            } else if (claimVal instanceof String && claimVal.toString().contains("[\"")
                    && claimVal.toString().contains("\"]")) {

                try {
                    List<String> arrayList = mapper.readValue(claimVal.toString(), List.class);
                    jwtClaimSetBuilder.claim(claimEntry.getKey(), arrayList);
                } catch (IOException e) {
                    // Exception isn't thrown in order to generate jwt without claim, even if an error is
                    // occurred during the retrieving claims.
                    log.error("Error while reading claim values", e);
                }
            } else if (JWTConstants.EXPIRY_TIME.equals(claimEntry.getKey())) {
                long exp = TimeUnit.SECONDS.toMillis(Long.parseLong((String) claimEntry.getValue()));
                jwtClaimSetBuilder.claim(claimEntry.getKey(), new Date(exp));
            } else if (JWTConstants.ISSUED_TIME.equals(claimEntry.getKey())) {
                long iat = TimeUnit.SECONDS.toMillis(Long.parseLong((String) claimEntry.getValue()));
                jwtClaimSetBuilder.claim(claimEntry.getKey(), new Date(iat));
            } else {
                jwtClaimSetBuilder.claim(claimEntry.getKey(), claimEntry.getValue());
            }
        }

        //Adding JWT standard claim
        jwtClaimSetBuilder.jwtID(UUID.randomUUID().toString());
        JWTClaimsSet jwtClaimsSet = jwtClaimSetBuilder.build();
        return jwtClaimsSet.toJSONObject().toString();
    }

    public String encode(byte[] stringToBeEncoded) throws JWTGeneratorException {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(stringToBeEncoded);
    }

    public String getDialectURI() {
        return dialectURI;
    }

    public abstract Map<String, Object> populateStandardClaims(JWTInfoDto jwtInfoDto);

    public abstract Map<String, ClaimValueDTO> populateCustomClaims(JWTInfoDto jwtInfoDto);
}

