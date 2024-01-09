// Copyright 2022 The Nomulus Authors. All Rights Reserved.
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

package google.registry.request;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_DISPOSITION;
import static com.google.common.net.HttpHeaders.CONTENT_LENGTH;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.utils.IOUtils;

/** Utilities for common functionality relating to {@link URLConnection}s. */
public final class UrlConnectionUtils {

  private UrlConnectionUtils() {}

  /**
   * Retrieves the response from the given connection as a byte array.
   *
   * <p>Note that in the event the response code is 4XX or 5XX, we use the error stream as any
   * payload is included there.
   *
   * @see HttpURLConnection#getErrorStream()
   */
  public static byte[] getResponseBytes(HttpURLConnection connection) throws IOException {
    int responseCode = connection.getResponseCode();
    try (InputStream is =
        responseCode < 400 ? connection.getInputStream() : connection.getErrorStream()) {
      return ByteStreams.toByteArray(is);
    } catch (NullPointerException e) {
      return new byte[] {};
    }
  }

  /** Decodes compressed data in GZIP format. */
  public static byte[] gUnzipBytes(byte[] bytes) throws IOException {
    try (GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
      return IOUtils.toByteArray(inputStream);
    }
  }

  /** Checks whether {@code bytes} are GZIP encoded. */
  public static boolean isGZipped(byte[] bytes) {
    // See GzipOutputStream.writeHeader()
    return (bytes.length > 2 && bytes[0] == (byte) GZIPInputStream.GZIP_MAGIC)
        && (bytes[1] == (byte) (GZIPInputStream.GZIP_MAGIC >> 8));
  }

  /** Sets auth on the given connection with the given username/password. */
  public static void setBasicAuth(HttpURLConnection connection, String username, String password) {
    setBasicAuth(connection, String.format("%s:%s", username, password));
  }

  /** Sets auth on the given connection with the given string, formatted "username:password". */
  public static void setBasicAuth(HttpURLConnection connection, String usernameAndPassword) {
    String token = base64().encode(usernameAndPassword.getBytes(UTF_8));
    connection.setRequestProperty(AUTHORIZATION, "Basic " + token);
  }

  /** Sets the given byte[] payload on the given connection with a particular content type. */
  public static void setPayload(HttpURLConnection connection, byte[] bytes, String contentType)
      throws IOException {
    connection.setRequestProperty(CONTENT_TYPE, contentType);
    connection.setDoOutput(true);
    try (DataOutputStream dataStream = new DataOutputStream(connection.getOutputStream())) {
      dataStream.write(bytes);
    }
  }

  /**
   * Sets payload on request as a {@code multipart/form-data} request.
   *
   * <p>This is equivalent to running the command: {@code curl -F fieldName=@payload.txt URL}
   *
   * @see <a href="http://www.ietf.org/rfc/rfc2388.txt">RFC2388 - Returning Values from Forms</a>
   */
  public static void setPayloadMultipart(
      HttpURLConnection connection,
      String name,
      String filename,
      MediaType contentType,
      String data,
      Random random)
      throws IOException {
    String boundary = createMultipartBoundary(random);
    checkState(
        !data.contains(boundary), "Multipart data contains autogenerated boundary: %s", boundary);
    String multipart =
        String.format("--%s\r\n", boundary)
            + String.format(
                "%s: form-data; name=\"%s\"; filename=\"%s\"\r\n",
                CONTENT_DISPOSITION, name, filename)
            + String.format("%s: %s\r\n", CONTENT_TYPE, contentType)
            + "\r\n"
            + data
            + "\r\n"
            + String.format("--%s--\r\n", boundary);
    byte[] payload = multipart.getBytes(UTF_8);
    connection.setRequestProperty(CONTENT_LENGTH, Integer.toString(payload.length));
    setPayload(
        connection, payload, String.format("multipart/form-data;" + " boundary=\"%s\"", boundary));
  }

  private static String createMultipartBoundary(Random random) {
    // Generate 192 random bits (24 bytes) to produce 192/log_2(64) = 192/6 = 32 base64 digits.
    byte[] rand = new byte[24];
    random.nextBytes(rand);
    // Boundary strings can be up to 70 characters long, so use 30 hyphens plus 32 random digits.
    // See https://tools.ietf.org/html/rfc2046#section-5.1.1
    return Strings.repeat("-", 30) + base64().encode(rand);
  }
}
