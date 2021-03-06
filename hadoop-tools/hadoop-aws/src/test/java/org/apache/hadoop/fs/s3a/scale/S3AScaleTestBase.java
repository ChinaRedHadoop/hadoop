/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.hadoop.fs.s3a.scale;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.contract.ContractTestUtils;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import org.apache.hadoop.fs.Path;

import org.apache.hadoop.fs.s3a.S3AInputStream;
import org.apache.hadoop.fs.s3a.S3AInstrumentation;
import org.apache.hadoop.fs.s3a.S3ATestConstants;
import org.apache.hadoop.fs.s3a.S3ATestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Locale;

import static org.junit.Assume.assumeTrue;

/**
 * Base class for scale tests; here is where the common scale configuration
 * keys are defined.
 */
public class S3AScaleTestBase extends Assert implements S3ATestConstants {

  @Rule
  public TestName methodName = new TestName();

  @BeforeClass
  public static void nameThread() {
    Thread.currentThread().setName("JUnit");
  }

  protected S3AFileSystem fs;

  protected static final Logger LOG =
      LoggerFactory.getLogger(S3AScaleTestBase.class);

  private Configuration conf;

  /**
   * Configuration generator. May be overridden to inject
   * some custom options.
   * @return a configuration with which to create FS instances
   */
  protected Configuration createConfiguration() {
    return new Configuration();
  }

  /**
   * Get the configuration used to set up the FS.
   * @return the configuration
   */
  public Configuration getConf() {
    return conf;
  }

  @Before
  public void setUp() throws Exception {
    conf = createConfiguration();
    LOG.debug("Scale test operation count = {}", getOperationCount());
    fs = S3ATestUtils.createTestFileSystem(conf);
  }

  @After
  public void tearDown() throws Exception {
    ContractTestUtils.rm(fs, getTestPath(), true, true);
  }

  protected Path getTestPath() {
    String testUniqueForkId = System.getProperty("test.unique.fork.id");
    return testUniqueForkId == null ? new Path("/tests3a") :
        new Path("/" + testUniqueForkId, "tests3a");
  }

  protected long getOperationCount() {
    return getConf().getLong(KEY_OPERATION_COUNT, DEFAULT_OPERATION_COUNT);
  }

  /**
   * Describe a test in the logs
   * @param text text to print
   * @param args arguments to format in the printing
   */
  protected void describe(String text, Object... args) {
    LOG.info("\n\n{}: {}\n",
        methodName.getMethodName(),
        String.format(text, args));
  }

  /**
   * Get the input stream statistics of an input stream.
   * Raises an exception if the inner stream is not an S3A input stream
   * @param in wrapper
   * @return the statistics for the inner stream
   */
  protected S3AInstrumentation.InputStreamStatistics getInputStreamStatistics(
      FSDataInputStream in) {
    InputStream inner = in.getWrappedStream();
    if (inner instanceof S3AInputStream) {
      S3AInputStream s3a = (S3AInputStream) inner;
      return s3a.getS3AStreamStatistics();
    } else {
      Assert.fail("Not an S3AInputStream: " + inner);
      // never reached
      return null;
    }
  }

  /**
   * Make times more readable, by adding a "," every three digits.
   * @param nanos nanos or other large number
   * @return a string for logging
   */
  protected static String toHuman(long nanos) {
    return String.format(Locale.ENGLISH, "%,d", nanos);
  }

  /**
   * Log the bandwidth of a timer as inferred from the number of
   * bytes processed.
   * @param timer timer
   * @param bytes bytes processed in the time period
   */
  protected void bandwidth(NanoTimer timer, long bytes) {
    LOG.info("Bandwidth = {}  MB/S",
        timer.bandwidthDescription(bytes));
  }

  /**
   * Work out the bandwidth in MB/s
   * @param bytes bytes
   * @param durationNS duration in nanos
   * @return the number of megabytes/second of the recorded operation
   */
  public static double bandwidthMBs(long bytes, long durationNS) {
    return (bytes * 1000.0 ) / durationNS;
  }

  /**
   * A simple class for timing operations in nanoseconds, and for
   * printing some useful results in the process.
   */
  protected static class NanoTimer {
    final long startTime;
    long endTime;

    public NanoTimer() {
      startTime = now();
    }

    /**
     * End the operation
     * @return the duration of the operation
     */
    public long end() {
      endTime = now();
      return duration();
    }

    /**
     * End the operation; log the duration
     * @param format message
     * @param args any arguments
     * @return the duration of the operation
     */
    public long end(String format, Object... args) {
      long d = end();
      LOG.info("Duration of {}: {} nS",
          String.format(format, args), toHuman(d));
      return d;
    }

    long now() {
      return System.nanoTime();
    }

    long duration() {
      return endTime - startTime;
    }

    double bandwidth(long bytes) {
      return S3AScaleTestBase.bandwidthMBs(bytes, duration());
    }

    /**
     * Bandwidth as bytes per second
     * @param bytes bytes in
     * @return the number of bytes per second this operation timed.
     */
    double bandwidthBytes(long bytes) {
      return (bytes * 1.0 ) / duration();
    }

    /**
     * How many nanoseconds per byte
     * @param bytes bytes processed in this time period
     * @return the nanoseconds it took each byte to be processed
     */
    long nanosPerByte(long bytes) {
      return duration() / bytes;
    }

    /**
     * Get a description of the bandwidth, even down to fractions of
     * a MB
     * @param bytes bytes processed
     * @return bandwidth
     */
    String bandwidthDescription(long bytes) {
      return String.format("%,.6f", bandwidth(bytes));
    }
  }
}
