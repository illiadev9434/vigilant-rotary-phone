// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.flows;

import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableMap;
import google.registry.testing.AppEngineRule;
import google.registry.testing.CertificateSamples;
import java.util.Optional;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test logging in with TLS credentials. */
@RunWith(JUnit4.class)
public class EppLoginTlsTest extends EppTestCase {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();


  void setClientCertificateHash(String clientCertificateHash) {
    setTransportCredentials(
        new TlsCredentials(clientCertificateHash, Optional.of("192.168.1.100:54321")));
  }

  @Before
  public void initTest() {
    persistResource(
        loadRegistrar("NewRegistrar")
            .asBuilder()
            .setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH)
            .build());
    // Set a cert for the second registrar, or else any cert will be allowed for login.
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH)
            .build());
  }

  @Test
  public void testLoginLogout() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
  }

  @Test
  public void testLogin_wrongPasswordFails() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    // For TLS login, we also check the epp xml password.
    assertThatLogin("NewRegistrar", "incorrect")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of("CODE", "2200", "MSG", "Registrar password is incorrect"));
  }

  @Test
  public void testMultiLogin() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
    assertThatLogoutSucceeds();
    assertThatLogin("TheRegistrar", "password2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2200", "MSG", "Registrar certificate does not match stored certificate"));
  }

  @Test
  public void testNonAuthedLogin_fails() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    assertThatLogin("TheRegistrar", "password2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2200", "MSG", "Registrar certificate does not match stored certificate"));
  }


  @Test
  public void testBadCertificate_failsBadCertificate2200() throws Exception {
    setClientCertificateHash("laffo");
    assertThatLogin("NewRegistrar", "foo-BAR2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of(
                "CODE", "2200", "MSG", "Registrar certificate does not match stored certificate"));
  }

  @Test
  public void testGfeDidntProvideClientCertificate_failsMissingCertificate2200() throws Exception {
    setClientCertificateHash("");
    assertThatLogin("NewRegistrar", "foo-BAR2")
        .hasResponse(
            "response_error.xml",
            ImmutableMap.of("CODE", "2200", "MSG", "Registrar certificate not present"));
  }

  @Test
  public void testGoodPrimaryCertificate() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(
        loadRegistrar("NewRegistrar")
            .asBuilder()
            .setClientCertificate(CertificateSamples.SAMPLE_CERT, now)
            .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
            .build());
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
  }

  @Test
  public void testGoodFailoverCertificate() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(
        loadRegistrar("NewRegistrar")
            .asBuilder()
            .setClientCertificate(CertificateSamples.SAMPLE_CERT, now)
            .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
            .build());
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
  }

  @Test
  public void testMissingPrimaryCertificateButHasFailover_usesFailover() throws Exception {
    setClientCertificateHash(CertificateSamples.SAMPLE_CERT2_HASH);
    DateTime now = DateTime.now(UTC);
    persistResource(
        loadRegistrar("NewRegistrar")
            .asBuilder()
            .setClientCertificate(null, now)
            .setFailoverClientCertificate(CertificateSamples.SAMPLE_CERT2, now)
            .build());
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
  }

  @Test
  public void testRegistrarHasNoCertificatesOnFile_disablesCertChecking() throws Exception {
    setClientCertificateHash("laffo");
    DateTime now = DateTime.now(UTC);
    persistResource(
        loadRegistrar("NewRegistrar")
            .asBuilder()
            .setClientCertificate(null, now)
            .setFailoverClientCertificate(null, now)
            .build());
    assertThatLoginSucceeds("NewRegistrar", "foo-BAR2");
  }
}
