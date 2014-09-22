/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import com.oracle.java.testlibrary.*;

/*
 * @test CheckSegmentedCodeCache
 * @bug 8015774
 * @summary "Checks VM options related to the segmented code cache"
 * @library /testlibrary
 * @run main/othervm CheckSegmentedCodeCache
 */
public class CheckSegmentedCodeCache {
  // Code heap names
  private static final String NON_METHOD = "CodeHeap 'non-methods'";
  private static final String PROFILED = "CodeHeap 'profiled nmethods'";
  private static final String NON_PROFILED = "CodeHeap 'non-profiled nmethods'";

  private static void verifySegmentedCodeCache(ProcessBuilder pb, boolean enabled) throws Exception {
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    if (enabled) {
      try {
        // Non-method code heap should be always available with the segmented code cache
        out.shouldContain(NON_METHOD);
      } catch (RuntimeException e) {
        // TieredCompilation is disabled in a client VM
        out.shouldContain("TieredCompilation is disabled in this release.");
      }
    } else {
      out.shouldNotContain(NON_METHOD);
    }
    out.shouldHaveExitValue(0);
  }

  private static void verifyCodeHeapNotExists(ProcessBuilder pb, String... heapNames) throws Exception {
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    for (String name : heapNames) {
      out.shouldNotContain(name);
    }
  }

  private static void failsWith(ProcessBuilder pb, String message) throws Exception {
    OutputAnalyzer out = new OutputAnalyzer(pb.start());
    out.shouldContain(message);
    out.shouldHaveExitValue(1);
  }

  /**
   * Check the result of segmented code cache related VM options.
   */
  public static void main(String[] args) throws Exception {
    ProcessBuilder pb;

    // Disabled with ReservedCodeCacheSize < 240MB
    pb = ProcessTools.createJavaProcessBuilder("-XX:ReservedCodeCacheSize=239m",
                                               "-XX:+PrintCodeCache", "-version");
    verifySegmentedCodeCache(pb, false);

    // Disabled without TieredCompilation
    pb = ProcessTools.createJavaProcessBuilder("-XX:-TieredCompilation",
                                               "-XX:+PrintCodeCache", "-version");
    verifySegmentedCodeCache(pb, false);

    // Enabled with TieredCompilation and ReservedCodeCacheSize >= 240MB
    pb = ProcessTools.createJavaProcessBuilder("-XX:+TieredCompilation",
                                               "-XX:ReservedCodeCacheSize=240m",
                                               "-XX:+PrintCodeCache", "-version");
    verifySegmentedCodeCache(pb, true);

    // Always enabled if SegmentedCodeCache is set
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-XX:-TieredCompilation",
                                               "-XX:ReservedCodeCacheSize=239m",
                                               "-XX:+PrintCodeCache", "-version");
    verifySegmentedCodeCache(pb, true);

    // The profiled and non-profiled code heaps should not be available in
    // interpreter-only mode
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-Xint",
                                               "-XX:+PrintCodeCache", "-version");
    verifyCodeHeapNotExists(pb, PROFILED, NON_PROFILED);
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-XX:TieredStopAtLevel=0",
                                               "-XX:+PrintCodeCache", "-version");
    verifyCodeHeapNotExists(pb, PROFILED, NON_PROFILED);

    // If we stop compilation at CompLevel_simple
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-XX:TieredStopAtLevel=1",
                                               "-XX:+PrintCodeCache", "-version");
    verifyCodeHeapNotExists(pb, PROFILED);

    // Fails with too small non-method code heap size
    pb = ProcessTools.createJavaProcessBuilder("-XX:NonMethodCodeHeapSize=100K");
    failsWith(pb, "Invalid NonMethodCodeHeapSize");

    // Fails if code heap sizes do not add up
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-XX:ReservedCodeCacheSize=10M",
                                               "-XX:NonMethodCodeHeapSize=5M",
                                               "-XX:ProfiledCodeHeapSize=5M",
                                               "-XX:NonProfiledCodeHeapSize=5M");
    failsWith(pb, "Invalid code heap sizes");

    // Fails if not enough space for VM internal code
    pb = ProcessTools.createJavaProcessBuilder("-XX:+SegmentedCodeCache",
                                               "-XX:ReservedCodeCacheSize=1700K",
                                               "-XX:InitialCodeCacheSize=100K");
    failsWith(pb, "Not enough space in non-method code heap to run VM");
  }
}
