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

package google.registry.tmch;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.tmch.TmchTestData.loadSmd;
import static org.junit.Assert.assertThrows;

import google.registry.config.RegistryConfig.ConfigModule.TmchCaMode;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import google.registry.tmch.TmchXmlSignature.CertificateSignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateRevokedException;
import javax.xml.crypto.dsig.XMLSignatureException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TmchXmlSignature}.
 *
 * <p>This class does not test the {@code revoked/smd/} folder because it's not a crypto issue.
 */
@RunWith(JUnit4.class)
public class TmchXmlSignatureTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastoreAndCloudSql().build();

  @Rule
  public final InjectRule inject = new InjectRule();

  // This should be a date which falls within the validity range of the test files contained in the
  // testdata/active directory. Note that test files claiming to be valid for a particular date
  // range in the file header may not actually be valid the whole time, because they contain an
  // embedded certificate which might have a shorter validity range.
  //
  // New versions of the test files are published every few years by ICANN, and available at in the
  // Signed Mark Data Files section of:
  //
  // https://newgtlds.icann.org/en/about/trademark-clearinghouse/registries-registrars
  //
  // The link labeled "Set of IDN test-SMDs" leads to a .tar.gz file containing the test files which
  // in our directory structure reside in testdata/active and testdata/revoked/smd (it is not clear
  // where the files in testdata/invalid and testdata/revoked/tmv come from; we probably made them
  // ourselves, since there aren't as many of them). For purposes of testing, we could probably keep
  // using old test files forever, and keep a corresponding old date, but it seems like a good idea
  // to keep up to date if possible.
  //
  // When updating this date, also update the dates below, which test to make sure that dates before
  // and after the validity window result in rejection.
  private final FakeClock clock = new FakeClock(DateTime.parse("2018-05-15T23:15:37.4Z"));

  private byte[] smdData;
  private TmchXmlSignature tmchXmlSignature =
      new TmchXmlSignature(new TmchCertificateAuthority(TmchCaMode.PILOT, clock));

  @Before
  public void before() {
  }

  @Test
  public void testWrongCertificateAuthority() {
    tmchXmlSignature =
        new TmchXmlSignature(new TmchCertificateAuthority(TmchCaMode.PRODUCTION, clock));
    smdData = loadSmd("active/Court-Agent-Arab-Active.smd");
    CertificateSignatureException e =
        assertThrows(CertificateSignatureException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("Signature does not match");
  }

  @Test
  public void testTimeTravelBeforeCertificateWasCreated() {
    smdData = loadSmd("active/Court-Agent-Arab-Active.smd");
    clock.setTo(DateTime.parse("2013-05-01T00:00:00Z"));
    assertThrows(CertificateNotYetValidException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testTimeTravelAfterCertificateHasExpired() {
    smdData = loadSmd("active/Court-Agent-Arab-Active.smd");
    clock.setTo(DateTime.parse("2023-06-01T00:00:00Z"));
    assertThrows(CertificateExpiredException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testActiveCourtAgentArabActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentChineseActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtAgentRussianActive() throws Exception {
    smdData = loadSmd("active/Court-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderArabActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderChineseActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveCourtHolderRussianActive() throws Exception {
    smdData = loadSmd("active/Court-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentArabActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentChineseActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkAgentRussianActive() throws Exception {
    smdData = loadSmd("active/Trademark-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderArabActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderChineseActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTrademarkHolderRussianActive() throws Exception {
    smdData = loadSmd("active/Trademark-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentArabActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentChineseActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentEnglishActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentFrenchActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteAgentRussianActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Agent-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderArabActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Arab-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderChineseActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Chinese-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderEnglishActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-English-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderFrenchActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-French-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testActiveTreatystatuteHolderRussianActive() throws Exception {
    smdData = loadSmd("active/TreatyStatute-Holder-Russian-Active.smd");
    tmchXmlSignature.verify(smdData);
  }

  @Test
  public void testInvalidInvalidsignatureCourtAgentFrenchActive() {
    smdData = loadSmd("invalid/InvalidSignature-Court-Agent-French-Active.smd");
    assertThrows(XMLSignatureException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testInvalidInvalidsignatureTrademarkAgentEnglishActive() {
    smdData = loadSmd("invalid/InvalidSignature-Trademark-Agent-English-Active.smd");
    assertThrows(XMLSignatureException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testInvalidInvalidsignatureTrademarkAgentRussianActive() {
    smdData = loadSmd("invalid/InvalidSignature-Trademark-Agent-Russian-Active.smd");
    assertThrows(XMLSignatureException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testInvalidInvalidsignatureTreatystatuteAgentChineseActive() {
    smdData = loadSmd("invalid/InvalidSignature-TreatyStatute-Agent-Chinese-Active.smd");
    assertThrows(XMLSignatureException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testInvalidInvalidsignatureTreatystatuteAgentEnglishActive() {
    smdData = loadSmd("invalid/InvalidSignature-TreatyStatute-Agent-English-Active.smd");
    assertThrows(XMLSignatureException.class, () -> tmchXmlSignature.verify(smdData));
  }

  @Test
  public void testRevokedTmvTmvrevokedCourtAgentFrenchActive() {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Court-Agent-French-Active.smd");
    CertificateRevokedException e =
        assertThrows(CertificateRevokedException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("KEY_COMPROMISE");
  }

  @Test
  public void testRevokedTmvTmvrevokedTrademarkAgentEnglishActive() {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Trademark-Agent-English-Active.smd");
    CertificateRevokedException e =
        assertThrows(CertificateRevokedException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("Certificate has been revoked");
  }

  @Test
  public void testRevokedTmvTmvrevokedTrademarkAgentRussianActive() {
    smdData = loadSmd("revoked/tmv/TMVRevoked-Trademark-Agent-Russian-Active.smd");
    CertificateRevokedException e =
        assertThrows(CertificateRevokedException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("Certificate has been revoked");
  }

  @Test
  public void testRevokedTmvTmvrevokedTreatystatuteAgentChineseActive() {
    smdData = loadSmd("revoked/tmv/TMVRevoked-TreatyStatute-Agent-Chinese-Active.smd");
    CertificateRevokedException e =
        assertThrows(CertificateRevokedException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("KEY_COMPROMISE");
  }

  @Test
  public void testRevokedTmvTmvrevokedTreatystatuteAgentEnglishActive() {
    smdData = loadSmd("revoked/tmv/TMVRevoked-TreatyStatute-Agent-English-Active.smd");
    CertificateRevokedException e =
        assertThrows(CertificateRevokedException.class, () -> tmchXmlSignature.verify(smdData));
    assertThat(e).hasMessageThat().contains("KEY_COMPROMISE");
  }
}
