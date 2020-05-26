package eu.arrowhead.core.certificate_authority;

import eu.arrowhead.common.SSLProperties;
import eu.arrowhead.common.database.entity.CaCertificate;
import eu.arrowhead.common.dto.internal.CertificateCheckRequestDTO;
import eu.arrowhead.common.dto.internal.CertificateCheckResponseDTO;
import eu.arrowhead.common.dto.internal.CertificateSigningRequestDTO;
import eu.arrowhead.common.dto.internal.CertificateSigningResponseDTO;
import eu.arrowhead.common.exception.BadPayloadException;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.exception.InvalidParameterException;
import eu.arrowhead.core.certificate_authority.database.CACertificateDBService;
import eu.arrowhead.core.certificate_authority.database.CACertificateDBServiceTestContext;
import eu.arrowhead.core.certificate_authority.database.CATrustedKeyDBService;
import eu.arrowhead.core.certificate_authority.database.CATrustedKeyDBServiceTestContext;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {CACertificateDBServiceTestContext.class, CATrustedKeyDBServiceTestContext.class})
public class CertificateAuthorityServiceTest {

    public static final long CA_CERT_ID = 999;
    private static final Pattern PEM_PATTERN = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
    private static final String CLOUD_CN = "testcloud2.aitia.arrowhead.eu";
    private static final String CONSUMER_CN = "consumer." + CLOUD_CN;
    private static final String SYSOP_CN = "sysop." + CLOUD_CN;
    private static final String SIGN_REQUESTER_DUMMY = "dummy";
    private static final String SIGN_REQUESTER_VALID = "valid." + CLOUD_CN;
    private static final String SIGN_REQUESTER_SYSOP = SYSOP_CN;

    @Rule
    public ExpectedException thrownException = ExpectedException.none();

    @MockBean(name = "mockCACertificateDBService")
    CACertificateDBService caCertificateDBService;

    @MockBean(name = "mockCATrustedKeyDBService")
    CATrustedKeyDBService caTrustedKeyDBService;

    @InjectMocks
    CertificateAuthorityService service;

    private CAProperties caProperties;

    private X509Certificate rootCertificate;
    private X509Certificate cloudCertificate;

    // =================================================================================================
    // methods

    @BeforeClass
    public static void globalSetup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static String getResourceContent(String resourcePath) throws IOException {
        File resource = new ClassPathResource(resourcePath).getFile();
        return new String(Files.readAllBytes(resource.toPath())).trim();
    }

    private static CertificateSigningRequestDTO buildRequest(final String csrResourcePath,
                                                             final ZonedDateTime validAfter,
                                                             final ZonedDateTime validBefore) throws IOException {
        final String encodedCSR = getResourceContent(csrResourcePath);
        return new CertificateSigningRequestDTO(encodedCSR, validAfter, validBefore);
    }

    private static CertificateSigningRequestDTO buildRequest(final String csrResourcePath) throws IOException {
        return buildRequest(csrResourcePath, null, null);
    }

    @Before
    public void setUp() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        caProperties = getCAProperties();
        ReflectionTestUtils.setField(service, "caProperties", caProperties);
        ReflectionTestUtils.setField(service, "sslProperties", getSslProperties());
        ReflectionTestUtils.setField(service, "certificateDbService", caCertificateDBService);

        service.init();

        rootCertificate = (X509Certificate) ReflectionTestUtils.getField(service, "rootCertificate");
        cloudCertificate = (X509Certificate) ReflectionTestUtils.getField(service, "cloudCertificate");

        when(caCertificateDBService.saveCertificateInfo(anyString(), any(), anyString()))
                .thenReturn(new CaCertificate(CA_CERT_ID));
    }

    private SSLProperties getSslProperties() {
        SSLProperties sslProperties = mock(SSLProperties.class);
        when(sslProperties.getKeyPassword()).thenReturn("123456");
        when(sslProperties.getKeyStorePassword()).thenReturn("123456");
        when(sslProperties.getKeyStoreType()).thenReturn("PKCS12");
        when(sslProperties.getKeyStore()).thenReturn(new ClassPathResource("certificates/certificate_authority.p12"));

        return sslProperties;
    }

    private CAProperties getCAProperties() {
        CAProperties caProperties = mock(CAProperties.class);
        when(caProperties.getCertValidityNegativeOffsetMinutes()).thenReturn(1L);
        when(caProperties.getCertValidityPositiveOffsetMinutes()).thenReturn(60L);

        return caProperties;
    }

    // -------------------------------------------------------------------------------------------------

    @Test
    public void testGetCloudCommonName() {
        assertEquals(service.getCloudCommonName(), "testcloud2.aitia.arrowhead.eu");
    }

    @Test(expected = InvalidParameterException.class)
    public void testCheckCertificateNull() {
        service.checkCertificate(null);
    }

    @Test(expected = InvalidParameterException.class)
    public void testCheckCertificateEmpty() {
        service.checkCertificate(new CertificateCheckRequestDTO());
    }

    @Test(expected = InvalidParameterException.class)
    public void testCheckCertificateInvalidString() {
        service.checkCertificate(new CertificateCheckRequestDTO(0, "Invalid CSR String"));
    }

    @Test(expected = InvalidParameterException.class)
    public void testCheckCertificateValidPEM() throws IOException {
        final String pemCert = getResourceContent("certificates/sysop.pem");
        service.checkCertificate(new CertificateCheckRequestDTO(0, pemCert));
    }

    @Test(expected = InvalidParameterException.class)
    public void testCheckCertificateValidBase64DERNotFound() throws IOException {
        final String pemCert = getResourceContent("certificates/sysop.pem");
        final String encodedCert = PEM_PATTERN.matcher(pemCert).replaceFirst("$1");
        final CertificateCheckRequestDTO request = new CertificateCheckRequestDTO(0, encodedCert);

        when(caCertificateDBService.isCertificateValidNow(any())).thenThrow(DataNotFoundException.class);

        CertificateCheckResponseDTO response = service.checkCertificate(request);

        verify(caCertificateDBService, times(1)).isCertificateValidNow(any());
        assertEquals(response.getCommonName(), SYSOP_CN);
        assertEquals(response.getStatus(), "unknown");
    }

    @Test()
    public void testCheckCertificateValidBase64DERFound() throws IOException {
        final String pemCert = getResourceContent("certificates/sysop.pem");
        final String encodedCert = PEM_PATTERN.matcher(pemCert)
                .replaceFirst("$1")
                .replace("\n", "")
                .replace("\r", "");
        final CertificateCheckRequestDTO request = new CertificateCheckRequestDTO(0, encodedCert);

        CertificateCheckResponseDTO responseDTO = new CertificateCheckResponseDTO(SYSOP_CN, BigInteger.ONE, "good", ZonedDateTime.now());

        when(caCertificateDBService.isCertificateValidNow(any())).thenReturn(responseDTO);

        CertificateCheckResponseDTO response = service.checkCertificate(request);

        verify(caCertificateDBService, times(1)).isCertificateValidNow(any());
        assertEquals(response.getCommonName(), SYSOP_CN);
    }

    // ------------------------------------------------------------------------

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateNull1() {
        service.signCertificate(null, null);
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateNull2() {
        service.signCertificate(null, SIGN_REQUESTER_DUMMY);
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateNull3() {
        service.signCertificate(new CertificateSigningRequestDTO(), null);
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateEmpty1() {
        service.signCertificate(new CertificateSigningRequestDTO(), "");
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateEmpty2() {
        service.signCertificate(new CertificateSigningRequestDTO(SIGN_REQUESTER_DUMMY), "");
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateEmpty3() {
        service.signCertificate(new CertificateSigningRequestDTO(""), SIGN_REQUESTER_DUMMY);
    }

    @Test(expected = BadPayloadException.class)
    public void testSignCertificateInvalidString() {
        service.signCertificate(new CertificateSigningRequestDTO("Invalid CSR"), SIGN_REQUESTER_DUMMY);
    }

    @Test
    public void testSignCertificateValidBase64DerCsr() throws IOException {
        final CertificateSigningRequestDTO request = buildRequest("certificates/consumer.csr");

        final CertificateSigningResponseDTO response = service.signCertificate(request, SIGN_REQUESTER_VALID);

        verify(caCertificateDBService).saveCertificateInfo(eq(CONSUMER_CN), any(), eq(SIGN_REQUESTER_VALID));
        verifyCertSigningResponse(response, CONSUMER_CN);
    }

    @Test(expected = InvalidParameterException.class)
    public void testSignCertificateProtectedBase64DerCsr() throws IOException {
        final CertificateSigningRequestDTO request = buildRequest("certificates/sysop.csr");

        service.signCertificate(request, SIGN_REQUESTER_VALID);

        verify(caCertificateDBService, never()).saveCertificateInfo(eq(SYSOP_CN), any(), eq(SIGN_REQUESTER_VALID));
    }

    @Test
    public void testSignCertificateProtectedBase64DerCsrSysopRequest() throws IOException {
        final CertificateSigningRequestDTO request = buildRequest("certificates/sysop.csr");

        final CertificateSigningResponseDTO response = service.signCertificate(request, SIGN_REQUESTER_SYSOP);

        verify(caCertificateDBService).saveCertificateInfo(eq(SYSOP_CN), any(), eq(SIGN_REQUESTER_SYSOP));
        verifyCertSigningResponse(response, SYSOP_CN);
    }

    private X509Certificate verifyCertSigningResponse(CertificateSigningResponseDTO response, String commonName) {
        assertNotNull(response);
        assertNotNull(commonName);
        assertEquals(response.getId(), CA_CERT_ID);

        final List<String> certificateChain = response.getCertificateChain();
        assertNotNull(certificateChain);
        assertEquals(certificateChain.size(), 3);

        final X509Certificate clientCert = CertificateAuthorityUtils.decodeCertificate(certificateChain.get(0));
        assertEquals(CertificateAuthorityUtils.getCommonName(clientCert), commonName);
        assertEquals(CertificateAuthorityUtils.decodeCertificate(certificateChain.get(1)), cloudCertificate);
        assertEquals(CertificateAuthorityUtils.decodeCertificate(certificateChain.get(2)), rootCertificate);

        return clientCert;
    }
}
